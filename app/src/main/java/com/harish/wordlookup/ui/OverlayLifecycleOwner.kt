package com.harish.wordlookup.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * A WindowManager-attached view (the overlay) has no Activity behind it, so
 * Compose's lifecycle/viewmodel-store/saved-state hooks need a manual owner.
 *
 * **Single-use.** [stop] drives the registry to DESTROYED, which is terminal:
 * `LifecycleRegistry` refuses to climb back out of it, and
 * `performRestore` refuses to run twice ("SavedStateRegistry was already
 * restored."). Reusing one instance across two overlays therefore throws on
 * the second [start] - which, inside the accessibility service, crashes the
 * service and makes Android disable it. Create a fresh owner per attachment;
 * see OverlayHost.ensureViewAttached.
 */
class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val viewModelStoreInstance = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = viewModelStoreInstance
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun start() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
