package kg.edu.tin.liangbuliang

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 右上角的「检查更新」按钮。检查中与下载中都在原位显示进度环，
 * 下载时按比例显示，用户不用打开对话框也能看出进展。
 */
@Composable
fun UpdateCheckButton(
    state: UpdateState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val idleTint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
    IconButton(
        onClick = onClick,
        modifier = modifier.testTag("check_update_button")
    ) {
        when (state) {
            is UpdateState.Checking -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = idleTint
            )

            is UpdateState.Downloading -> CircularProgressIndicator(
                progress = { state.fraction },
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )

            else -> Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = "检查更新",
                tint = idleTint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** 发现新版本、以及下载进度两个对话框。 */
@Composable
fun UpdateDialogs(
    state: UpdateState,
    progressVisible: Boolean,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onHideProgress: () -> Unit,
    onDismiss: () -> Unit
) {
    when {
        state is UpdateState.Available -> AvailableDialog(
            state = state,
            onDownload = onDownload,
            onDismiss = onDismiss
        )

        state is UpdateState.Downloading && progressVisible -> ProgressDialog(
            state = state,
            onHideProgress = onHideProgress,
            onCancelDownload = onCancelDownload
        )
    }
}

@Composable
private fun AvailableDialog(
    state: UpdateState.Available,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val onBg = MaterialTheme.colorScheme.onBackground
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("update_available_dialog"),
        title = { Text("发现新版本") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.currentVersion,
                        fontSize = 14.sp,
                        color = onBg.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "  →  ",
                        fontSize = 14.sp,
                        color = onBg.copy(alpha = 0.5f)
                    )
                    Text(
                        text = state.latestVersion,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "将从官网直接下载安装包，下载完成后自动启动安装。",
                    fontSize = 12.sp,
                    color = onBg.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload, modifier = Modifier.testTag("update_download")) {
                Text("下载更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后") }
        }
    )
}

@Composable
private fun ProgressDialog(
    state: UpdateState.Downloading,
    onHideProgress: () -> Unit,
    onCancelDownload: () -> Unit
) {
    val onBg = MaterialTheme.colorScheme.onBackground
    val indeterminate = state.totalBytes <= 0
    val percent = (state.fraction * 100).toInt()

    AlertDialog(
        // 点对话框外部等同「后台下载」：下载本身在前台服务里继续，不会中断。
        onDismissRequest = onHideProgress,
        modifier = Modifier.testTag("update_progress_dialog"),
        title = { Text("正在下载 ${state.version}") },
        text = {
            Column {
                if (indeterminate) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { state.fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (indeterminate) "已下载 ${formatBytes(state.bytesRead)}"
                        else "$percent%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (indeterminate) ""
                        else "${formatBytes(state.bytesRead)} / ${formatBytes(state.totalBytes)}",
                        fontSize = 13.sp,
                        color = onBg.copy(alpha = 0.6f)
                    )
                }
                if (state.bytesPerSecond > 0) {
                    Text(
                        text = "${formatBytes(state.bytesPerSecond)}/s",
                        fontSize = 12.sp,
                        color = onBg.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onHideProgress) { Text("后台下载") }
        },
        dismissButton = {
            TextButton(onClick = onCancelDownload) { Text("取消") }
        }
    )
}
