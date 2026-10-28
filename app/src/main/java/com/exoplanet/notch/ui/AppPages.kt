package com.exoplanet.notch.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.exoplanet.notch.update.GitHubUpdate
import com.exoplanet.notch.update.UpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class AppPage {
    Player,
    Guide,
    About,
    Update,
}

@Composable
internal fun AppPageScreen(page: AppPage, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val title = when (page) {
        AppPage.About -> "About"
        AppPage.Update -> "Update"
        else -> "Guide"
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Ink)
        when (page) {
            AppPage.About -> AboutBody()
            AppPage.Update -> UpdateBody()
            else -> GuideBody()
        }
    }
}

@Composable
private fun AboutBody() {
    val context = LocalContext.current
    val version = remember(context) { versionLabel(context) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AboutRow("Version", version)
        AboutRow("Author", "Allen Jensen")
        Text(
            "Notch plays audio you choose and cuts narrow bands out of it. The file stays on this phone. Update only downloads a newer release from GitHub.",
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
        )
    }
}

@Composable
private fun UpdateBody() {
    val context = LocalContext.current
    val current = remember(context) { versionLabel(context).substringBefore(" ") }
    var attempt by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Checking GitHub for a newer release…") }
    var checking by remember { mutableStateOf(true) }

    LaunchedEffect(attempt) {
        checking = true
        status = "Checking GitHub for a newer release…"
        val outcome = withContext(Dispatchers.IO) {
            runCatching { GitHubUpdate.check(current) }
        }
        outcome.onFailure { error ->
            checking = false
            status = error.message ?: "Could not check for an update."
            return@LaunchedEffect
        }
        when (val result = outcome.getOrThrow()) {
            is UpdateCheck.UpToDate -> {
                checking = false
                status = "Notch ${result.current} is the latest release."
            }
            is UpdateCheck.Available -> {
                if (!GitHubUpdate.canInstallPackages(context)) {
                    checking = false
                    status = "Allow Notch to install unknown apps, then tap Check again."
                    GitHubUpdate.requestInstallPermission(context)
                    return@LaunchedEffect
                }
                status = "Downloading Notch ${result.version}…"
                val file = withContext(Dispatchers.IO) {
                    runCatching { GitHubUpdate.download(context, result.url) }
                }
                file.onFailure { error ->
                    checking = false
                    status = error.message ?: "Could not download the update."
                    return@LaunchedEffect
                }
                checking = false
                status = "Opening the installer for Notch ${result.version}."
                runCatching { GitHubUpdate.launchInstaller(context, file.getOrThrow()) }
                    .onFailure { status = "Could not open the installer." }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(status, style = MaterialTheme.typography.bodyLarge, color = Ink)
        if (!checking) {
            Button(onClick = { attempt += 1 }) { Text("Check again") }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Muted)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = Ink)
    }
}

@Composable
private fun GuideBody() {
    val context = LocalContext.current
    val markdown = remember(context) {
        context.assets.open("guide.md").bufferedReader().use { it.readText() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        markdown.lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            if (line.isBlank()) return@forEach
            val body = MaterialTheme.typography.bodyLarge
            when {
                line.startsWith("# ") -> MarkdownText(
                    line.removePrefix("# "),
                    MaterialTheme.typography.headlineSmall,
                )
                line.startsWith("## ") -> MarkdownText(
                    line.removePrefix("## "),
                    MaterialTheme.typography.titleLarge,
                )
                line.startsWith("- ") -> MarkdownText("•  ${line.removePrefix("- ")}", body)
                else -> MarkdownText(line, body)
            }
        }
    }
}

@Composable
private fun MarkdownText(line: String, style: androidx.compose.ui.text.TextStyle) {
    val context = LocalContext.current
    val accent = Teal
    val annotated = buildAnnotatedString {
        appendMarkdown(line, context, accent)
    }
    BasicText(text = annotated, style = style.copy(color = Ink))
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendMarkdown(
    line: String,
    context: Context,
    accent: androidx.compose.ui.graphics.Color,
) {
    var index = 0
    while (index < line.length) {
        val boldAt = line.indexOf("**", index)
        val linkAt = line.indexOf("[", index)
        val next = listOf(boldAt, linkAt).filter { it >= 0 }.minOrNull() ?: line.length
        if (next > index) append(line.substring(index, next))
        if (next == line.length) break
        if (next == boldAt) {
            val end = line.indexOf("**", next + 2)
            if (end < 0) {
                append(line.substring(next))
                break
            }
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = accent)) {
                append(line.substring(next + 2, end))
            }
            index = end + 2
            continue
        }
        val closeLabel = line.indexOf("](", next)
        val closeUrl = if (closeLabel > next) line.indexOf(")", closeLabel + 2) else -1
        if (closeLabel < 0 || closeUrl < 0) {
            append(line[next])
            index = next + 1
            continue
        }
        val label = line.substring(next + 1, closeLabel)
        val url = line.substring(closeLabel + 2, closeUrl)
        withLink(
            LinkAnnotation.Url(
                url = url,
                styles = TextLinkStyles(
                    SpanStyle(color = accent, textDecoration = TextDecoration.Underline),
                ),
                linkInteractionListener = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
            ),
        ) {
            append(label)
        }
        index = closeUrl + 1
    }
}

private fun versionLabel(context: Context): String {
    val info = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val code = if (Build.VERSION.SDK_INT >= 28) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
    return "${info.versionName} ($code)"
}
