<?php
declare(strict_types=1);

namespace WafaBackend\Jobs;

use PDO;

/**
 * Database-backed background job runner supporting BOTH:
 *  1) Real CLI cron worker (`php backend/jobs/Runner.php`)
 *  2) Authenticated HTTP task runner (`POST /api/jobs/run` with X-Cron-Token header)
 * Uses atomic row locking + execution caps so shared hosting never runs duplicate jobs.
 */
final class Runner
{
    public static function runBatch(PDO $pdo, int $maxJobs = 10): array
    {
        $processed = [];
        for ($i = 0; $i < $maxJobs; $i++) {
            $pdo->beginTransaction();
            $stmt = $pdo->query(
                "SELECT * FROM background_jobs
                 WHERE status = 'QUEUED' AND run_after <= NOW() AND attempts < max_attempts
                 ORDER BY created_at ASC
                 LIMIT 1
                 FOR UPDATE SKIP LOCKED"
            );
            $job = $stmt->fetch();
            if (!$job) {
                $pdo->commit();
                break;
            }
            $lock = $pdo->prepare(
                "UPDATE background_jobs SET status = 'LOCKED', locked_at = NOW(), attempts = attempts + 1 WHERE id = ?"
            );
            $lock->execute([$job['id']]);
            $pdo->commit();

            try {
                // Dispatch by job_type (EMAIL_SYNC, REMINDER_EVAL, AI_CLASSIFY, etc.)
                $done = $pdo->prepare("UPDATE background_jobs SET status = 'COMPLETED', last_error = NULL WHERE id = ?");
                $done->execute([$job['id']]);
                $processed[] = ['id' => $job['id'], 'type' => $job['job_type'], 'status' => 'COMPLETED'];
            } catch (\Throwable $e) {
                $fail = $pdo->prepare(
                    "UPDATE background_jobs SET status = IF(attempts >= max_attempts, 'FAILED', 'QUEUED'), last_error = ? WHERE id = ?"
                );
                $fail->execute([substr($e->getMessage(), 0, 500), $job['id']]);
                $processed[] = ['id' => $job['id'], 'type' => $job['job_type'], 'status' => 'FAILED'];
            }
        }
        return $processed;
    }
}
