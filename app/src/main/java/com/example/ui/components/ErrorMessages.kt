package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.domain.model.AppError

object ErrorMessages {
    @Composable
    fun resolve(error: AppError): String {
        return when (error) {
            is AppError.Network -> stringResource(R.string.error_network)
            is AppError.SourceUnavailable -> stringResource(
                R.string.error_source_unavailable,
                error.sourceId
            )
            is AppError.ParseFailure -> stringResource(
                R.string.error_parse_failure,
                error.sourceId,
                error.what
            )
            is AppError.RateLimited -> stringResource(R.string.error_rate_limited)
            is AppError.NotFound -> stringResource(R.string.error_not_found)
            is AppError.Unknown -> {
                if (error.cause.isNullOrBlank()) {
                    stringResource(R.string.error_unknown_generic)
                } else {
                    stringResource(R.string.error_unknown, error.cause)
                }
            }
        }
    }
}
