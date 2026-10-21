package com.r0adkll.cassette.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0adkll.cassette.common.compose.components.Chevron
import com.r0adkll.cassette.common.compose.components.CodeBadge
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.components.PanelItem
import com.r0adkll.cassette.common.compose.components.SettingsRow
import com.r0adkll.cassette.common.compose.components.SettingsSection
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.LicenceProjectScreen
import com.r0adkll.cassette.common.screens.LicenceTextScreen
import com.r0adkll.cassette.common.screens.LicencesScreen
import com.r0adkll.cassette.settings.ui.resources.Res
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.produceRetainedState
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlin.coroutines.cancellation.CancellationException

sealed interface LoadState<out T> {
  data object Loading : LoadState<Nothing>

  data object Failed : LoadState<Nothing>

  data class Loaded<T>(val value: T) : LoadState<T>
}

/** Runs [load] once per [key], keeping the result across configuration changes. */
@Composable
private fun <T> produceLoad(key: Any?, load: suspend () -> T): LoadState<T> =
  produceRetainedState<LoadState<T>>(LoadState.Loading, key) {
      value =
        try {
          LoadState.Loaded(load())
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          LoadState.Failed
        }
    }
    .value

// Licences: projects, fonts and icons, licence texts.

data class LicencesUiState(
  val catalog: LoadState<LicenceCatalog>,
  val onBack: () -> Unit,
  val onProject: (String) -> Unit,
  val onLicence: (String) -> Unit,
) : CircuitUiState

@CircuitInject(LicencesScreen::class, AppScope::class)
@Inject
class LicencesPresenter(
  private val navigator: Navigator,
  private val store: LicenceCatalogStore,
) : Presenter<LicencesUiState> {
  @Composable
  override fun present() =
    LicencesUiState(
      catalog = produceLoad(Unit) { store.load() },
      onBack = { navigator.pop() },
      onProject = { navigator.goTo(LicenceProjectScreen(it)) },
      onLicence = { navigator.goTo(LicenceTextScreen(it)) },
    )
}

/**
 * Third-party notices (decision 0012: Lucide ISC; fonts OFL): the build's libraries as one row per
 * project, the fonts and icons we bundle by hand, and each licence's full text.
 */
@CircuitInject(LicencesScreen::class, AppScope::class)
@Composable
fun LicencesUi(state: LicencesUiState, modifier: Modifier = Modifier) {
  val uriHandler = LocalUriHandler.current
  TitledScreen("Licences.", onBack = state.onBack, modifier) {
    Text(
      "Cassette is built on open-source work. Thank you to everyone behind it.",
      style = CassetteTheme.type.body,
      color = CassetteTheme.colors.muted,
    )
    val catalog = (state.catalog as? LoadState.Loaded)?.value
    if (state.catalog == LoadState.Failed) FailedNote()
    if (catalog != null)
      SettingsSection("Libraries · ${catalog.libraryCount}") {
        catalog.projects.forEachIndexed { index, project ->
          SettingsRow(
            project.name,
            subtitle =
              listOf(
                  libraries(project.libraries.size),
                  project.licences.mapNotNull { catalog.licence(it)?.shortName }.joinToString(", "),
                )
                .filter { it.isNotEmpty() }
                .joinToString(" · "),
            leading = { CodeBadge(project.code) },
            onClick = { state.onProject(project.name) },
            divider = index != catalog.projects.lastIndex,
          )
        }
      }
    SettingsSection("Fonts and icons") {
      BUNDLED.forEachIndexed { index, work ->
        SettingsRow(
          work.name,
          subtitle = "${work.licence} · ${work.authors}",
          leading = { CodeBadge(work.code) },
          onClick = { state.onLicence(work.key) },
          divider = index != BUNDLED.lastIndex,
        )
      }
    }
    if (catalog != null)
      SettingsSection("Licence texts") {
        catalog.licences.forEachIndexed { index, licence ->
          LicenceRow(
            licence,
            subtitle = libraries(licence.libraryCount),
            onOpen = { state.onLicence(licence.key) },
            onLink = { uriHandler.openUri(it) },
            divider = index != catalog.licences.lastIndex,
          )
        }
      }
  }
}

// A project: its licences and website, then every library.

@Immutable data class ProjectDetail(val project: LibraryProject, val licences: List<LicenceInfo>)

data class LicenceProjectUiState(
  val name: String,
  val detail: LoadState<ProjectDetail>,
  val onBack: () -> Unit,
  val onLicence: (String) -> Unit,
) : CircuitUiState

@CircuitInject(LicenceProjectScreen::class, AppScope::class)
@Inject
class LicenceProjectPresenter(
  private val screen: LicenceProjectScreen,
  private val navigator: Navigator,
  private val store: LicenceCatalogStore,
) : Presenter<LicenceProjectUiState> {
  @Composable
  override fun present() =
    LicenceProjectUiState(
      name = screen.project,
      detail =
        produceLoad(screen) {
          val catalog = store.load()
          val project = checkNotNull(catalog.project(screen.project))
          ProjectDetail(project, project.licences.mapNotNull { catalog.licence(it) })
        },
      onBack = { navigator.pop() },
      onLicence = { navigator.goTo(LicenceTextScreen(it)) },
    )
}

@CircuitInject(LicenceProjectScreen::class, AppScope::class)
@Composable
fun LicenceProjectUi(state: LicenceProjectUiState, modifier: Modifier = Modifier) {
  val uriHandler = LocalUriHandler.current
  val detail = (state.detail as? LoadState.Loaded)?.value
  TitledLazyScreen(
    "${state.name}.",
    onBack = state.onBack,
    modifier,
    kicker = detail?.project?.organisation,
  ) {
    if (state.detail == LoadState.Failed) item { FailedNote() }
    if (detail == null) return@TitledLazyScreen
    val project = detail.project
    item {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
          "${libraries(project.libraries.size)} in this build.",
          style = CassetteTheme.type.body,
          color = CassetteTheme.colors.muted,
        )
        Panel {
          detail.licences.forEachIndexed { index, licence ->
            val count = project.libraries.count { licence.key in it.licences }
            LicenceRow(
              licence,
              subtitle =
                if (count == project.libraries.size) "All of them"
                else "$count of ${project.libraries.size}",
              onOpen = { state.onLicence(licence.key) },
              onLink = { uriHandler.openUri(it) },
              divider = index != detail.licences.lastIndex || project.website != null,
            )
          }
          if (project.website != null)
            SettingsRow(
              "Website",
              subtitle = project.website.substringAfter("://").substringBefore('/'),
              icon = LucideIcons.Globe,
              onClick = { uriHandler.openUri(project.website) },
              divider = false,
              trailing = { ExternalLinkIcon() },
            )
        }
        Kicker("Libraries", Modifier.padding(top = 14.dp, bottom = 6.dp))
      }
    }
    itemsIndexed(project.libraries, key = { _, library -> library.coordinates }) { index, library ->
      PanelItem(index, project.libraries.size) {
        SettingsRow(
          library.name,
          subtitle = library.coordinates,
          divider = index != project.libraries.lastIndex,
          trailing = {
            if (library.version != null)
              Text(
                library.version,
                style = CassetteTheme.type.numeral.copy(fontSize = 16.sp),
                color = CassetteTheme.colors.muted,
              )
          },
        )
      }
    }
  }
}

// A licence's full text, or a bundled work's notice.

@Immutable
data class LicenceText(
  val kicker: String,
  val title: String,
  val intro: String?,
  /** The text to show; null when only [url] has it. */
  val body: String?,
  val url: String?,
)

data class LicenceTextUiState(val text: LoadState<LicenceText>, val onBack: () -> Unit) :
  CircuitUiState

@CircuitInject(LicenceTextScreen::class, AppScope::class)
@Inject
class LicenceTextPresenter(
  private val screen: LicenceTextScreen,
  private val navigator: Navigator,
  private val store: LicenceCatalogStore,
) : Presenter<LicenceTextUiState> {
  @Composable
  override fun present() =
    LicenceTextUiState(
      text =
        produceLoad(screen) {
          val work = BUNDLED.find { it.key == screen.licence }
          if (work != null)
            LicenceText(
              kicker = work.licence,
              title = work.name,
              intro = null,
              body = Res.readBytes(work.file).decodeToString().trim(),
              url = null,
            )
          else {
            val licence = checkNotNull(store.load().licence(screen.licence))
            LicenceText(
              kicker = libraries(licence.libraryCount),
              title = licence.name,
              intro = summary(licence.projects),
              body = licence.text,
              url = licence.url,
            )
          }
        },
      onBack = { navigator.pop() },
    )
}

@CircuitInject(LicenceTextScreen::class, AppScope::class)
@Composable
fun LicenceTextUi(state: LicenceTextUiState, modifier: Modifier = Modifier) {
  val uriHandler = LocalUriHandler.current
  val text = (state.text as? LoadState.Loaded)?.value
  TitledScreen(text?.title?.let { "$it." } ?: "", onBack = state.onBack, modifier, text?.kicker) {
    if (state.text == LoadState.Failed) FailedNote()
    if (text == null) return@TitledScreen
    if (text.intro != null)
      Text(text.intro, style = CassetteTheme.type.body, color = CassetteTheme.colors.muted)
    Panel {
      when {
        text.body != null ->
          Text(
            text.body,
            style = CassetteTheme.type.mono,
            color = CassetteTheme.colors.text,
            modifier = Modifier.padding(vertical = 4.dp),
          )
        text.url != null ->
          SettingsRow(
            "Read the licence",
            subtitle = text.url.substringAfter("://").substringBefore('/'),
            icon = LucideIcons.FileText,
            onClick = { uriHandler.openUri(text.url) },
            divider = false,
            trailing = { ExternalLinkIcon() },
          )
      }
    }
  }
}

/** A licence that opens its text, or its web page when the export has no text. */
@Composable
private fun LicenceRow(
  licence: LicenceInfo,
  subtitle: String,
  onOpen: () -> Unit,
  onLink: (String) -> Unit,
  divider: Boolean,
) {
  val link = licence.url.takeIf { licence.text == null }
  SettingsRow(
    licence.name,
    subtitle = subtitle,
    icon = LucideIcons.FileText,
    onClick =
      when {
        licence.text != null -> onOpen
        link != null -> ({ onLink(link) })
        else -> null
      },
    divider = divider,
    trailing = { if (link != null) ExternalLinkIcon() else if (licence.text != null) Chevron() },
  )
}

@Composable
private fun ExternalLinkIcon() {
  Icon(
    LucideIcons.ExternalLink,
    null,
    tint = CassetteTheme.colors.muted,
    modifier = Modifier.size(18.dp),
  )
}

@Composable
private fun FailedNote() {
  Text(
    "Couldn't read the library list.",
    style = CassetteTheme.type.body,
    color = CassetteTheme.colors.muted,
  )
}

private fun libraries(count: Int) = if (count == 1) "1 library" else "$count libraries"

/** "AndroidX, Kotlin and Ktor.", or the first five "and 10 more." */
internal fun summary(names: List<String>): String? {
  if (names.isEmpty()) return null
  val shown = names.take(5)
  val rest = names.size - shown.size
  return when {
    rest > 0 -> shown.joinToString(", ") + " and $rest more."
    shown.size == 1 -> "${shown[0]}."
    else -> shown.dropLast(1).joinToString(", ") + " and ${shown.last()}."
  }
}
