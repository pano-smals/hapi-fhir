INSERT INTO BT2_JOB_INSTANCE (
   ID,
   JOB_CANCELLED,
   CMB_RECS_PROCESSED,
   CMB_RECS_PER_SEC,
   CREATE_TIME,
   CUR_GATED_STEP_ID,
   DEFINITION_ID,
   DEFINITION_VER,
   END_TIME,
   ERROR_COUNT,
   EST_REMAINING,
   PARAMS_JSON,
   PROGRESS_PCT,
   START_TIME,
   STAT,
   TOT_ELAPSED_MILLIS,
   ERROR_MSG,
   PARAMS_JSON_LOB,
   WORK_CHUNKS_PURGED
) VALUES (
   '00161699-bcfe-428e-9ca2-caceb9645f8a',
   0,
   0,
   0,
   SYSDATE,
   'WriteBundleForImportStep',
   'bulkImportJob',
   1,
   SYSDATE,
   0,
   '0ms',
   '{"jobId":"42bfa0dd-ab7b-4991-8284-e4b2902c696b","batchSize":100}',
   1,
   SYSDATE,
   'COMPLETED',
   200,
   'Error message',
   HEXTORAW('8B9D5255'),
   1
);

INSERT INTO BT2_WORK_CHUNK (
   ID,
   CREATE_TIME,
   END_TIME,
   ERROR_COUNT,
   INSTANCE_ID,
   DEFINITION_ID,
   DEFINITION_VER,
   RECORDS_PROCESSED,
   SEQ,
   START_TIME,
   STAT,
   TGT_STEP_ID,
   ERROR_MSG,
   CHUNK_DATA
) VALUES (
   '01d26875-8d1a-4e37-b554-62a3219f009b',
   SYSDATE,
   SYSDATE,
   0,
   '00161699-bcfe-428e-9ca2-caceb9645f8a',
   'bulkImportJob',
   1,
   0,
   0,
   SYSDATE,
   'COMPLETED',
   'ReadInResourcesFromFileStep',
   'Error message',
   HEXTORAW('453d7a34')
);
