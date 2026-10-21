package com.sdprep.scheduler;

/**
 * Runs one kind of job. Jobs can run more than once (a worker may crash after doing the work but
 * before reporting success), so handlers should be idempotent: e.g. "send email unless already sent".
 */
@FunctionalInterface
public interface JobHandler {

    void run(Job job) throws Exception;
}
