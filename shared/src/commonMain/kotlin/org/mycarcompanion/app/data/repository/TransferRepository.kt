package org.mycarcompanion.app.data.repository

// Required Supabase table:
// CREATE TABLE vehicle_transfers (
//   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
//   vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
//   from_user_id UUID NOT NULL REFERENCES auth.users(id),
//   transfer_code TEXT NOT NULL UNIQUE,
//   claimed_by_id UUID REFERENCES auth.users(id),
//   claimed_at TIMESTAMPTZ,
//   expires_at TIMESTAMPTZ NOT NULL,
//   created_at TIMESTAMPTZ DEFAULT NOW()
// );
// ALTER TABLE vehicle_transfers ENABLE ROW LEVEL SECURITY;
// -- Owners can manage their transfers
// CREATE POLICY "Owners can manage their own transfers"
//   ON vehicle_transfers FOR ALL USING (from_user_id = auth.uid());
// -- Authenticated users can look up unclaimed transfers by code (to claim)
// CREATE POLICY "Authenticated users can read unclaimed transfers"
//   ON vehicle_transfers FOR SELECT
//   USING (auth.uid() IS NOT NULL AND claimed_by_id IS NULL);

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Clock
import org.mycarcompanion.app.data.models.VehicleTransfer
import org.mycarcompanion.app.data.models.VehicleTransferInsert

class TransferRepository(private val client: SupabaseClient) {

    private val table get() = client.postgrest["vehicle_transfers"]
    private val vehicles get() = client.postgrest["vehicles"]

    suspend fun createTransfer(vehicleId: String, code: String, expiresAt: String): Result<VehicleTransfer> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        table.insert(
            VehicleTransferInsert(
                vehicleId = vehicleId,
                fromUserId = userId,
                transferCode = code,
                expiresAt = expiresAt,
            )
        ) { select() }.decodeSingle<VehicleTransfer>()
    }

    suspend fun getActiveTransfersForVehicle(vehicleId: String): Result<List<VehicleTransfer>> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        val now = Clock.System.now().toString()
        // Fetch all transfers for this vehicle/user, filter active ones client-side
        table.select {
            filter {
                eq("vehicle_id", vehicleId)
                eq("from_user_id", userId)
            }
            order("created_at", Order.DESCENDING)
        }.decodeList<VehicleTransfer>()
            .filter { it.claimedById == null && it.expiresAt > now }
    }

    suspend fun lookupByCode(code: String): Result<VehicleTransfer?> = runCatching {
        val now = Clock.System.now().toString()
        table.select {
            filter { eq("transfer_code", code.trim().uppercase()) }
        }.decodeList<VehicleTransfer>()
            .firstOrNull { it.claimedById == null && it.expiresAt > now }
    }

    suspend fun claimTransfer(transfer: VehicleTransfer): Result<Unit> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        vehicles.update({ set("owner_id", userId) }) {
            filter { eq("id", transfer.vehicleId) }
        }
        val now = Clock.System.now().toString()
        table.update({
            set("claimed_by_id", userId)
            set("claimed_at", now)
        }) {
            filter { eq("id", transfer.id) }
        }
    }
}
