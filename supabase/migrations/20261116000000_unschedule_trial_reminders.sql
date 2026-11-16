-- No free trial exists, so stop the daily trial reminder job.
select cron.unschedule('check-trial-reminders-daily');
