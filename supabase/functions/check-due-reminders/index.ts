import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-cron-secret',
};

serve(async (req) => {
  // Handle CORS preflight requests
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  // Cron-only: require the shared secret so this can't be triggered publicly.
  const cronSecret = Deno.env.get('CRON_SECRET');
  if (!cronSecret || req.headers.get('x-cron-secret') !== cronSecret) {
    return new Response(
      JSON.stringify({ error: 'Unauthorized' }),
      { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }

  try {
    console.log('[Reminder Check] Starting reminder check...');

    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const supabaseKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    const supabase = createClient(supabaseUrl, supabaseKey);

    const today = new Date().toISOString().split('T')[0];
    console.log(`[Reminder Check] Checking reminders due on or before: ${today}`);

    // Get all active reminders that are due (by date or mileage)
    // We'll focus on date-based reminders for push notifications
    const { data: dueReminders, error: remindersError } = await supabase
      .from('reminders')
      .select(`
        id,
        type,
        custom_name,
        next_due_date,
        next_due_mileage,
        vehicle_id,
        vehicles!inner (
          id,
          make,
          model,
          year,
          owner_id,
          odometer
        )
      `)
      .eq('is_active', true)
      .lte('next_due_date', today);

    if (remindersError) {
      console.error('[Reminder Check] Error fetching reminders:', remindersError);
      return new Response(
        JSON.stringify({ error: 'Failed to fetch reminders' }),
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    console.log(`[Reminder Check] Found ${dueReminders?.length || 0} due reminders`);

    if (!dueReminders || dueReminders.length === 0) {
      return new Response(
        JSON.stringify({ message: 'No due reminders found', processed: 0 }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Group reminders by owner
    const remindersByOwner: Record<string, typeof dueReminders> = {};

    for (const reminder of dueReminders) {
      const vehicle = (reminder.vehicles as unknown) as { owner_id: string; make: string; model: string; year: number };
      const ownerId = vehicle.owner_id;

      if (!remindersByOwner[ownerId]) {
        remindersByOwner[ownerId] = [];
      }
      remindersByOwner[ownerId].push(reminder);
    }

    console.log(`[Reminder Check] Grouped reminders for ${Object.keys(remindersByOwner).length} owners`);

    // Check notification preferences and send notifications
    let notificationsSent = 0;
    const errors: string[] = [];

    for (const [ownerId, ownerReminders] of Object.entries(remindersByOwner)) {
      try {
        // Check if user has a registered device token (FCM)
        const { data: deviceTokens, error: tokenErr } = await supabase
          .from('device_tokens')
          .select('token')
          .eq('user_id', ownerId)
          .limit(1);

        if (tokenErr || !deviceTokens || deviceTokens.length === 0) {
          console.log(`[Reminder Check] No device tokens for user ${ownerId}`);
          continue;
        }

        // Get user's notification preferences
        const { data: prefs } = await supabase
          .from('notification_preferences')
          .select('*')
          .eq('user_id', ownerId)
          .maybeSingle();

        // Default to all notifications enabled if no preferences set
        const preferences = prefs || {
          oil_change: true,
          tire_rotation: true,
          brake_inspection: true,
          battery_check: true,
          air_filter: true,
          transmission: true,
          coolant: true,
          custom_reminders: true,
        };

        // Filter reminders based on preferences
        const enabledReminders = ownerReminders.filter(reminder => {
          const typeKey = reminder.type.toLowerCase().replace(/\s+/g, '_') as keyof typeof preferences;
          // If it's a custom reminder, check custom_reminders preference
          if (reminder.custom_name) {
            return preferences.custom_reminders !== false;
          }
          return preferences[typeKey] !== false;
        });

        if (enabledReminders.length === 0) {
          console.log(`[Reminder Check] All reminders filtered out by preferences for user ${ownerId}`);
          continue;
        }

        // Build notification message
        const reminderCount = enabledReminders.length;
        const firstReminder = enabledReminders[0];
        const vehicle = (firstReminder.vehicles as unknown) as { make: string; model: string; year: number };

        let title: string;
        let body: string;

        if (reminderCount === 1) {
          const reminderName = firstReminder.custom_name || firstReminder.type;
          title = `${reminderName} Due`;
          body = `Your ${vehicle.year} ${vehicle.make} ${vehicle.model} needs ${reminderName.toLowerCase()}.`;
        } else {
          title = `${reminderCount} Maintenance Reminders Due`;
          body = `You have ${reminderCount} maintenance items due for your vehicles.`;
        }

        // Send notification via the send-push-notification function
        // (service-role Bearer token is recognized as an internal caller there)
        const { error: pushError } = await supabase.functions.invoke('send-push-notification', {
          body: {
            recipient_id: ownerId,
            title,
            body,
          },
        });

        if (pushError) {
          console.error(`[Reminder Check] Failed to send notification to ${ownerId}:`, pushError);
          errors.push(`User ${ownerId}: ${pushError.message}`);
        } else {
          notificationsSent++;
          console.log(`[Reminder Check] Notification sent to user ${ownerId}`);
        }
      } catch (error) {
        const errorMsg = error instanceof Error ? error.message : 'Unknown error';
        console.error(`[Reminder Check] Error processing user ${ownerId}:`, error);
        errors.push(`User ${ownerId}: ${errorMsg}`);
      }
    }

    console.log(`[Reminder Check] Completed. Sent ${notificationsSent} notifications.`);

    return new Response(
      JSON.stringify({
        message: 'Reminder check completed',
        totalDueReminders: dueReminders.length,
        ownersNotified: notificationsSent,
        errors: errors.length > 0 ? errors : undefined,
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : 'Unknown error';
    console.error('[Reminder Check] Error:', error);
    return new Response(
      JSON.stringify({ error: errorMessage }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
