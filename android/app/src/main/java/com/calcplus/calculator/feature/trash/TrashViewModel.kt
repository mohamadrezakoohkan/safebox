package com.calcplus.calculator.feature.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcplus.calculator.core.domain.model.TrashPolicy
import com.calcplus.calculator.core.domain.repository.TrashContents
import com.calcplus.calculator.core.domain.repository.TrashItemId
import com.calcplus.calculator.core.domain.repository.TrashRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * "Recently deleted" (decisions §3).
 *
 * Restores and purges run in [workScope], not `viewModelScope`: emptying the
 * trash deletes rows and then files, and popping the screen (or a lock) must
 * not cancel it half way. The screen passes `container.applicationScope`, the
 * same scope erase-everything uses.
 */
class TrashViewModel(
    private val repository: TrashRepository,
    private val workScope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    /** null = first emission pending — render nothing, never a false "Nothing here". */
    val contents: StateFlow<TrashContents?> = repository.observeTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun restore(item: TrashItemId) {
        workScope.launch { repository.restore(listOf(item)) }
    }

    fun purge(item: TrashItemId) {
        workScope.launch { repository.purge(listOf(item)) }
    }

    fun emptyAll() {
        workScope.launch { repository.emptyAll() }
    }

    /** Whole days until this item expires, rounded up; 0 once it is due. */
    fun daysLeft(deletedAt: Long?): Int {
        val stamp = deletedAt ?: return TrashPolicy.RETENTION_DAYS
        return TrashPolicy.daysLeft(stamp, now())
    }
}
