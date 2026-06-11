package com.example.magneticcamera.core.export

import com.example.magneticcamera.domain.scan.GridCellMeasurement
import com.example.magneticcamera.domain.scan.ScanSession

class CsvExporter {
    fun export(session: ScanSession): String {
        val header = "session_id,cell_id,row,col,sample_count,captured_at_millis,x_mean,y_mean,z_mean," +
            "magnitude_mean,magnitude_median,magnitude_min,magnitude_max,magnitude_stddev," +
            "magnitude_delta_mean,vector_delta_mean,vector_delta_median,vector_delta_min," +
            "vector_delta_max,vector_delta_stddev,accuracy"
        val rows = session.cells.sortedWith(compareBy({ it.row }, { it.col })).map { cell ->
            listOf(
                session.id,
                cell.id,
                cell.row,
                cell.col,
                cell.sampleCount,
                cell.capturedAtMillis,
                cell.xMean,
                cell.yMean,
                cell.zMean,
                cell.magnitudeMean,
                cell.magnitudeMedian,
                cell.magnitudeMin,
                cell.magnitudeMax,
                cell.magnitudeStdDev,
                cell.magnitudeDeltaMean,
                cell.vectorDeltaMean,
                cell.vectorDeltaMedian,
                cell.vectorDeltaMin,
                cell.vectorDeltaMax,
                cell.vectorDeltaStdDev,
                cell.accuracy
            ).joinToString(",") { formatCsvValue(it) }
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun formatCsvValue(value: Any): String {
        val text = when (value) {
            is Float -> value.csvNumber()
            is Double -> value.toFloat().csvNumber()
            else -> value.toString()
        }
        return escapeCsv(text)
    }

    private fun Float.csvNumber(): String {
        return if (isFinite()) toString() else "0.0"
    }

    private fun escapeCsv(value: String): String {
        return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
