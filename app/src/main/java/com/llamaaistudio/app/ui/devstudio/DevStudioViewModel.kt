package com.llamaaistudio.app.ui.devstudio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.llamaaistudio.app.LlamaAiStudioApp
import com.llamaaistudio.app.data.db.DevProjectEntity
import com.llamaaistudio.app.devstudio.TermuxBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class DevStudioUiState(
    val selectedProject: DevProjectEntity? = null,
    val files: List<File> = emptyList(),
    val openFile: File? = null,
    val openFileContent: String = "",
    val dirty: Boolean = false,
    val status: String? = null,
)

class DevStudioViewModel(private val app: LlamaAiStudioApp) : ViewModel() {
    private val repo = app.devProjectRepository

    val projects: StateFlow<List<DevProjectEntity>> =
        repo.projects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(DevStudioUiState())
    val uiState: StateFlow<DevStudioUiState> = _uiState

    val termuxInstalled: Boolean get() = TermuxBridge.isTermuxInstalled(app)
    val toolchainComponents = TermuxBridge.components

    fun createProject(name: String, template: String) {
        viewModelScope.launch {
            val project = repo.createProject(name, template)
            openProject(project)
        }
    }

    fun openProject(project: DevProjectEntity) {
        viewModelScope.launch { repo.touchOpened(project) }
        val files = repo.listFiles(File(project.rootPath))
        _uiState.value = DevStudioUiState(selectedProject = project, files = files)
    }

    fun closeProject() {
        _uiState.value = DevStudioUiState()
    }

    fun closeFile() {
        _uiState.value = _uiState.value.copy(openFile = null, openFileContent = "", dirty = false)
    }

    fun deleteProject(project: DevProjectEntity) {
        viewModelScope.launch {
            repo.deleteProject(project)
            if (_uiState.value.selectedProject?.id == project.id) closeProject()
        }
    }

    fun openFile(file: File) {
        viewModelScope.launch {
            val content = repo.readFile(file)
            _uiState.value = _uiState.value.copy(openFile = file, openFileContent = content, dirty = false)
        }
    }

    fun updateOpenFileContent(content: String) {
        _uiState.value = _uiState.value.copy(openFileContent = content, dirty = true)
    }

    fun saveOpenFile() {
        val file = _uiState.value.openFile ?: return
        viewModelScope.launch {
            repo.writeFile(file, _uiState.value.openFileContent)
            _uiState.value = _uiState.value.copy(dirty = false, status = "Saved ${file.name}")
        }
    }

    fun exportToDownloads() {
        val project = _uiState.value.selectedProject ?: return
        viewModelScope.launch {
            val dest = repo.exportToDownloads(project)
            _uiState.value = _uiState.value.copy(status = "Exported to $dest")
        }
    }

    fun buildViaTermux() {
        val project = _uiState.value.selectedProject ?: return
        viewModelScope.launch {
            repo.exportToDownloads(project)
            val termuxPath = "~/storage/downloads/LlamaAiStudioProjects/${project.name}"
            val ok = TermuxBridge.runCommand(
                app,
                "./gradlew assembleDebug 2>&1 | tee build_output.log",
                termuxPath,
            )
            _uiState.value = _uiState.value.copy(
                status = if (ok) {
                    "Sent to Termux. Run `termux-setup-storage` once if you haven't, then check " +
                        "$termuxPath/build_output.log for progress."
                } else {
                    "Could not reach Termux. Install it and enable allow-external-apps in " +
                        "~/.termux/termux.properties, then try again."
                },
            )
        }
    }

    fun clearStatus() {
        _uiState.value = _uiState.value.copy(status = null)
    }

    class Factory(private val app: LlamaAiStudioApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DevStudioViewModel(app) as T
    }
}
