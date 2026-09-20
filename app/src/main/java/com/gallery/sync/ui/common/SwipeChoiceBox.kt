package com.gallery.sync.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** How far a swipe has to travel before letting go acts. Restore's folder cards use the same. */
private const val SwipeThresholdPx = 90f

/** The card moves this fraction of the finger's travel: a pull with some weight to it. */
private const val SwipeResistance = 0.55f

/** The furthest the card is drawn aside, in px. */
private const val MaxPullPx = 130f

/** Back to rest after a swipe: a quick spring with a slight overshoot, so it settles rather than stops. */
private val SwipeReturnSpring =
    spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

/**
 * A card that can be swiped right or left, drawn aside as it is pulled, with what letting go will do
 * shown in the space it uncovers: a tick on the right, a cross on the left.
 *
 * The same feel as a folder card on the Restore tab (same threshold, resistance, spring and tick
 * of haptic feedback), written once here for the Archive tab so the two do not drift. Restore's
 * folder card still carries its own copy and could move to this.
 *
 * ### Directional, not a toggle
 *
 * Right and left are separate callbacks, and the caller makes the one that does not apply a no-op.
 * That is what lets a run of swipes down a list never undo one that was already made: swiping left
 * on a file that is already kept does nothing, and so does swiping right on one that is not.
 *
 * ### For anyone not swiping
 *
 * [accessibilityLabel] and [onAccessibilityAction] give a screen reader the same choice as a named
 * action, since a drag is not something it can perform.
 *
 * @param stateKey anything the callbacks depend on, so the gesture is rebuilt when it changes; a
 * stale callback is the failure this exists to prevent.
 * @param content the card itself, handed the modifier that draws it aside.
 */
@Composable
fun SwipeChoiceBox(
    enabled: Boolean,
    stateKey: Any?,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    accessibilityLabel: String,
    onAccessibilityAction: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme

    val pull = remember { Animatable(0f) }

    // The gesture below is started once and kept while its keys stay the same, so it must not hold the
    // callbacks it was first given: a list that reorders or shrinks puts a different file in the same
    // slot, and a swipe would then act on the old one.
    val currentRight by rememberUpdatedState(onSwipeRight)
    val currentLeft by rememberUpdatedState(onSwipeLeft)

    // 0 at rest, 1 once the pull is far enough that letting go will act.
    val progress = (abs(pull.value) / (SwipeThresholdPx * SwipeResistance)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                if (enabled) {
                    customActions = listOf(
                        CustomAccessibilityAction(accessibilityLabel) {
                            onAccessibilityAction()
                            true
                        }
                    )
                }
            }
            .pointerInput(stateKey, enabled) {
                if (!enabled) return@pointerInput
                var travelled = 0f
                var crossed = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        travelled = 0f
                        crossed = false
                        scope.launch { pull.stop() }
                    },
                    onDragEnd = {
                        when {
                            travelled > SwipeThresholdPx -> currentRight()
                            travelled < -SwipeThresholdPx -> currentLeft()
                        }
                        scope.launch { pull.animateTo(0f, SwipeReturnSpring) }
                    },
                    onDragCancel = { scope.launch { pull.animateTo(0f, SwipeReturnSpring) } }
                ) { change, amount ->
                    travelled += amount
                    if (abs(travelled) > SwipeThresholdPx) change.consume()

                    // A short tick the moment the pull is far enough to act, and again if it is
                    // taken back under, so the finger can feel where the line is.
                    val past = abs(travelled) > SwipeThresholdPx
                    if (past != crossed) {
                        crossed = past
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }

                    val visual = (travelled * SwipeResistance).coerceIn(-MaxPullPx, MaxPullPx)
                    scope.launch { pull.snapTo(visual) }
                }
            }
    ) {
        if (pull.value != 0f) {
            val toRight = pull.value > 0f
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        (if (toRight) scheme.primaryContainer else scheme.surfaceVariant)
                            .copy(alpha = progress)
                    ),
                contentAlignment = if (toRight) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = if (toRight) SignalIcons.Check else SignalIcons.Cross,
                    contentDescription = null,
                    tint = if (toRight) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 22.dp)
                        .size(24.dp)
                        .graphicsLayer {
                            alpha = progress
                            scaleX = 0.6f + 0.4f * progress
                            scaleY = 0.6f + 0.4f * progress
                        }
                )
            }
        }

        content(Modifier.offset { IntOffset(pull.value.roundToInt(), 0) })
    }
}
