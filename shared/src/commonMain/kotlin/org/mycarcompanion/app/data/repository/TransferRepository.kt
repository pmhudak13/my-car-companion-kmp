package org.mycarcompanion.app.data.repository

// Required Supabase table:
// Actual vehicle_transfers schema:
//   id, vehicle_id, sender_id, transfer_code, expires_at, claimed_at, claimed_by, status, created_at

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
        table.select {
            filter {
                eq("vehicle_id", vehicleId)
                eq("sender_id", userId)
                exact("claimed_by", null)
                gt("expires_at", now)
            }
            order("created_at", Order.DESCENDING)
        }.decodeList<VehicleTransfer>()
    }

    suspend fun lookupByCode(code: String): Result<VehicleTransfer?> = runCatching {
        val now = Clock.System.now().toString()
        table.select {
            filter {
                eq("transfer_code", code.trim().uppercase())
                exact("claimed_by", null)
                gt("expires_at", now)
            }
        }.decodeList<VehicleTransfer>().firstOrNull()
    }

    suspend fun claimTransfer(transfer: VehicleTransfer): Result<Unit> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        client.postgrest.rpc(
            function = "claim_vehicle_transfer",
            parameters = buildJsonObject {
                put("p_code", transfer.transferCode)
                put("p_user_id", userId)
            },
        )
    }
}
