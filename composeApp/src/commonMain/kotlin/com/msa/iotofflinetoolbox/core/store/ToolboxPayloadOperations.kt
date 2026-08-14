package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.model.LogLevel
import com.msa.iotofflinetoolbox.core.model.PayloadOperation
import com.msa.iotofflinetoolbox.core.payload.PayloadTools

/** Pure payload transformation use-cases with UDF publication and audit logging. */
internal class ToolboxPayloadOperations(
    private val runner: ToolboxOperationRunner,
) : PayloadActions {
    override fun transformPayload(input: String, operation: PayloadOperation, variables: Map<String, String>) {
        val result = PayloadTools.transform(input, operation, variables)
        runner.update {
            copy(
                payloadResult = result,
                feedback = if (result.valid) null else AppFeedback.error(result.message, "Payload"),
            )
        }
        runner.log(
            if (result.valid) LogLevel.SUCCESS else LogLevel.ERROR,
            "Payload",
            "${operation.title}: ${result.message}",
        )
    }
}
