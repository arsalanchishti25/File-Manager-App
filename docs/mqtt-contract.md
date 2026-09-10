# MQTT Contract

Phase 2 uses QoS 1 for operation requests, routing responses, direct Aggregator
instructions, completion messages, and delete acknowledgements.

## Topics

| Topic | Publisher | Subscriber | Purpose |
| --- | --- | --- | --- |
| `filemanager/operations/request` | Main App | Load Balancer | Upload, download, and delete requests |
| `filemanager/operations/response/{mainAppId}` | Load Balancer | Main App | Routing response |
| `filemanager/aggregator/{aggregatorId}/upload` | Main App | Aggregator | Upload instruction |
| `filemanager/aggregator/{aggregatorId}/download` | Main App | Aggregator | Download instruction |
| `filemanager/aggregator/upload/complete/{mainAppId}` | Aggregator | Main App | Upload completion |
| `filemanager/aggregator/download/complete/{mainAppId}` | Aggregator | Main App | Download completion |
| `filemanager/storage/{fsContainerId}/delete` | Load Balancer | File Storage | Delete instruction |
| `filemanager/storage/delete-response` | File Storage | Load Balancer | Delete acknowledgement |
| `filemanager/events/error` | Aggregator/File Storage | Operations monitor | Structured validation or processing failure |

The Load Balancer and existing File Storage listener retain the legacy
`operations/request` and `fs/delete/response` aliases during migration.

## Identity and validation

Every Main App operation includes a unique `operationId`, a `correlationId`,
and the configured `mainAppId`. These values are copied into routing,
instruction, completion, failure, and delete acknowledgement messages.
Required identifiers, operation type, file identifiers, target lists, and
chunk metadata are validated before processing. Invalid messages produce a
structured error and do not terminate the service.

## Operation request

```json
{
  "operation": "UPLOAD",
  "operationId": "uuid",
  "correlationId": "uuid",
  "mainAppId": "main-app-1",
  "fileId": 42,
  "userId": 7,
  "filename": "report.pdf",
  "fileSize": 1200
}
```

## Direct upload instruction

```json
{
  "operationId": "uuid",
  "correlationId": "uuid",
  "mainAppId": "main-app-1",
  "sourceServiceId": "main-app-1",
  "fileId": 42,
  "filename": "report.pdf",
  "fileSize": 1200,
  "fsContainers": []
}
```

The file payload remains transferred through the existing SFTP path. The
instruction that starts Aggregator processing is delivered over MQTT.

## Direct download instruction

Download instructions carry the same identity fields, `fileId`, filename, and
the selected chunk target list. Aggregator download/reassembly is submitted to
a bounded executor rather than running in the Paho callback.

## Errors

```json
{
  "operationId": "uuid",
  "correlationId": "uuid",
  "mainAppId": "main-app-1",
  "sourceServiceId": "agg-1",
  "timestamp": 0,
  "errorCode": "PROCESSING_FAILED",
  "errorMessage": "Download processing failed"
}
```

Known error codes include `INVALID_JSON`, `MISSING_FIELD`, `INVALID_OPERATION`,
`INVALID_TARGET`, `EXECUTOR_REJECTED`, and `PROCESSING_FAILED`.
