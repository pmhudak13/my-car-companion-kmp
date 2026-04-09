package org.mycarcompanion.app.data.repository

// Required Supabase table:
// CREATE TABLE messages (
//   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
//   sender_id UUID NOT NULL REFERENCES auth.users(id),
//   recipient_id UUID NOT NULL REFERENCES auth.users(id),
//   vehicle_id UUID REFERENCES vehicles(id),
//   content TEXT NOT NULL,
//   is_read BOOLEAN DEFAULT FALSE,
//   created_at TIMESTAMPTZ DEFAULT NOW()
// );
// ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
// CREATE POLICY "Users can read their own messages"
//   ON messages FOR SELECT USING (sender_id = auth.uid() OR recipient_id = auth.uid());
// CREATE POLICY "Users can insert messages"
//   ON messages FOR INSERT WITH CHECK (sender_id = auth.uid());

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import org.mycarcompanion.app.data.models.Message

class MessageRepository(private val client: SupabaseClient) {

    private val table get() = client.postgrest["chat_messages"]

    suspend fun getInbox(): Result<List<Message>> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        table.select {
            filter { eq("recipient_id", userId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<Message>()
    }

    suspend fun getConversation(otherUserId: String): Result<List<Message>> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        table.select {
            filter {
                or {
                    and {
                        eq("sender_id", userId)
                        eq("recipient_id", otherUserId)
                    }
                    and {
                        eq("sender_id", otherUserId)
                        eq("recipient_id", userId)
                    }
                }
            }
            order("created_at", Order.ASCENDING)
        }.decodeList<Message>()
    }

    suspend fun sendMessage(recipientId: String, content: String, vehicleId: String? = null): Result<Unit> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        val payload = buildMap<String, Any?> {
            put("sender_id", userId)
            put("recipient_id", recipientId)
            put("content", content)
            if (vehicleId != null) put("vehicle_id", vehicleId)
        }
        table.insert(payload)
        Unit
    }

    suspend fun markAsRead(id: String): Result<Unit> = runCatching {
        table.update({ set("is_read", true) }) {
            filter { eq("id", id) }
        }
    }
}
