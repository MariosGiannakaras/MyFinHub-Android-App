package app.myfinhub.android.feature.money

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CARD_ASPECT_RATIO = 1.586f
private const val DELETE_COMMIT_THRESHOLD = 0.90f
private const val VISIBLE_STACK_LAYERS = 4

private data class StackLayout(
    val y: Dp,
    val scale: Float,
    val alpha: Float,
    val rotation: Float,
    val z: Float,
)

private val stackLayouts = listOf(
    StackLayout(0.dp, 1f, 1f, 0f, 30f),
    StackLayout(14.dp, .972f, .99f, -.5f, 29f),
    StackLayout(26.dp, .944f, .95f, .5f, 28f),
    StackLayout(37.dp, .916f, .89f, 0f, 27f),
)

private data class CardVisual(
    val templateId: String,
    val brandLabel: String,
    val colors: List<Color>,
    val text: Color,
    val muted: Color,
)

private val piraeusTemplates = listOf(
    Triple("piraeus-yellow", listOf(Color(0xFFFFE000), Color(0xFFF3C900)), Color(0xFF0B3852)),
    Triple("piraeus-virtual", listOf(Color(0xFFFFE99C), Color(0xFFF4D66D)), Color(0xFF0B3852)),
    Triple("piraeus-green", listOf(Color(0xFF003C3B), Color(0xFF002F39)), Color(0xFFEEE7D5)),
    Triple("piraeus-gold", listOf(Color(0xFFD9BB77), Color(0xFFB68B45)), Color(0xFF0A3D3C)),
    Triple("piraeus-platinum", listOf(Color(0xFFEDF3F7), Color(0xFFBCC9D6)), Color(0xFF1B3850)),
    Triple("piraeus-midnight", listOf(Color(0xFF123D53), Color(0xFF0A2535)), Color(0xFFE7EFE9)),
)

private val revolutTemplates = listOf(
    Triple("revolut", listOf(Color(0xFF1599D2), Color(0xFF3D54C6), Color(0xFFD22498)), Color.White),
    Triple("revolut-sage", listOf(Color(0xFF8DA996), Color(0xFF5D796F)), Color(0xFFF7F4ED)),
    Triple("revolut-midnight", listOf(Color(0xFF202A43), Color(0xFF10172A)), Color(0xFFF6F4ED)),
    Triple("revolut-slate", listOf(Color(0xFF6F7478), Color(0xFF40464B)), Color(0xFFF8F6F0)),
    Triple("revolut-lilac", listOf(Color(0xFFB07CFF), Color(0xFF6147C8)), Color.White),
    Triple("revolut-arctic", listOf(Color(0xFFF7FBFF), Color(0xFFBFD7EA)), Color(0xFF22354A)),
    Triple("revolut-ruby", listOf(Color(0xFF7A0829), Color(0xFFD1255B)), Color(0xFFFFF8FB)),
    Triple("revolut-emerald", listOf(Color(0xFF0C7D5C), Color(0xFF0A4F5E)), Color(0xFFF4FFF8)),
    Triple("revolut-metal-black", listOf(Color(0xFF35373A), Color(0xFF0D0E10)), Color(0xFFF4F3EF)),
    Triple("revolut-metal-gold", listOf(Color(0xFFD4B76F), Color(0xFF8D6C2D)), Color(0xFF342812)),
    Triple("revolut-metal-bronze", listOf(Color(0xFF9C6547), Color(0xFF543125)), Color(0xFFFFF6E7)),
    Triple("revolut-metal-silver", listOf(Color(0xFFE2E6E7), Color(0xFF949DA3)), Color(0xFF283039)),
    Triple("revolut-ultra", listOf(Color(0xFFF3F2EE), Color(0xFFC6C7C8)), Color(0xFF25262A)),
)

private val alphaTemplates = listOf(
    Triple("alpha", listOf(Color(0xFF83C4F4), Color(0xFF5DA3DE)), Color.White),
    Triple("alpha-bonus", listOf(Color(0xFF173F71), Color(0xFF0B274D)), Color(0xFFF6FBFF)),
    Triple("alpha-gold", listOf(Color(0xFF40351F), Color(0xFF1D1913)), Color(0xFFE7D0A0)),
    Triple("alpha-sky", listOf(Color(0xFF9AD2F8), Color(0xFF4F93D0)), Color.White),
    Triple("alpha-midnight", listOf(Color(0xFF183D72), Color(0xFF0A203F)), Color(0xFFEDF6FF)),
)

private val payzyTemplates = listOf(
    Triple("payzy", listOf(Color(0xFF6B03E8), Color(0xFF5100C7)), Color.White),
    Triple("payzy-physical", listOf(Color(0xFF43208F), Color(0xFF6248D2)), Color.White),
    Triple("payzy-pro", listOf(Color(0xFF63CFD0), Color(0xFF46B4BF)), Color.White),
    Triple("payzy-pro-night", listOf(Color(0xFF101B39), Color(0xFF060C20)), Color.White),
    Triple("payzy-neo", listOf(Color(0xFFFF2AB9), Color(0xFF4F00BE)), Color.White),
)

private val vivaTemplates = listOf(
    Triple("viva", listOf(Color(0xFF233458), Color(0xFF121A2F)), Color(0xFFF6F8FD)),
    Triple("viva-employee", listOf(Color(0xFF354767), Color(0xFF1A243C)), Color(0xFFF6F8FD)),
    Triple("viva-digital", listOf(Color(0xFF0F1B34), Color(0xFF08101F)), Color(0xFFF6F8FD)),
    Triple("viva-signature", listOf(Color(0xFF4D2F8A), Color(0xFF22184B)), Color(0xFFF6F8FD)),
    Triple("viva-carbon", listOf(Color(0xFF202020), Color(0xFF080B12)), Color(0xFFF6F8FD)),
    Triple("viva-cobalt", listOf(Color(0xFF0E5FB0), Color(0xFF113465)), Color(0xFFF6F8FD)),
)

@Composable
fun CreditCardStack(
    cards: List<MoneyCard>,
    secretState: CardSecretUiState,
    onActiveCardChanged: (String?) -> Unit,
    onRevealSecrets: () -> Unit,
    onHideSecrets: () -> Unit,
    onOpenCard: (String) -> Unit,
    onDeleteCard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ids = cards.map(MoneyCard::id)
    var order by remember { mutableStateOf(ids) }
    var pendingDeletedIds by remember { mutableStateOf(setOf<String>()) }
    var deleteArmedId by remember { mutableStateOf<String?>(null) }
    var deleteProgress by remember { mutableFloatStateOf(0f) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("") }
    val dragSettle = remember { Animatable(0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val reducedMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val thresholdPx = with(density) { 72.dp.toPx() }
    val restackDistancePx = with(density) { 92.dp.toPx() }

    LaunchedEffect(ids) {
        pendingDeletedIds = pendingDeletedIds.filterTo(mutableSetOf()) { it in ids }
        val visibleIds = ids.filterNot(pendingDeletedIds::contains)
        order = order.filter { it in visibleIds } + visibleIds.filterNot(order::contains)
    }

    val cardById = remember(cards) { cards.associateBy(MoneyCard::id) }
    val orderedCards = order.mapNotNull(cardById::get).filterNot { it.id in pendingDeletedIds }
    val activeCard = orderedCards.firstOrNull()
    val activeId = activeCard?.id

    LaunchedEffect(activeId) {
        deleteArmedId = null
        deleteProgress = 0f
        onActiveCardChanged(activeId)
    }

    val revealed = (secretState as? CardSecretUiState.Revealed)?.takeIf { it.cardId == activeId }
    val loading = (secretState as? CardSecretUiState.Loading)?.cardId == activeId

    fun rotateFrontToBack(direction: Int) {
        if (order.size < 2 || deletingId != null || deleteArmedId != null) return
        onHideSecrets()
        val start = dragOffset
        dragOffset = 0f
        dragging = false
        scope.launch {
            dragSettle.snapTo(start)
            if (!reducedMotion) {
                dragSettle.animateTo(
                    targetValue = direction.coerceIn(-1, 1) * restackDistancePx,
                    animationSpec = tween(150),
                )
            }
            order = order.drop(1) + order.first()
            dragSettle.snapTo(0f)
        }
    }

    fun finishDrag() {
        val finalOffset = dragOffset
        if (finalOffset.absoluteValue >= thresholdPx) {
            rotateFrontToBack(if (finalOffset < 0f) -1 else 1)
        } else {
            dragOffset = 0f
            dragging = false
            scope.launch {
                dragSettle.snapTo(finalOffset)
                if (!reducedMotion) dragSettle.animateTo(0f, tween(180)) else dragSettle.snapTo(0f)
            }
        }
    }

    fun copySecret(label: String, value: String?) {
        if (value.isNullOrBlank()) return
        clipboard.setText(AnnotatedString(value))
        statusMessage = "$label αντιγράφηκε"
    }

    fun commitDelete(cardId: String) {
        if (deletingId != null) return
        deleteArmedId = null
        deleteProgress = 0f
        onHideSecrets()
        deletingId = cardId
        scope.launch {
            if (!reducedMotion) delay(620)
            pendingDeletedIds = pendingDeletedIds + cardId
            order = order.filterNot { it == cardId }
            deletingId = null
            onDeleteCard(cardId)
            statusMessage = "Η κάρτα διαγράφηκε"
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(54.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .aspectRatio(CARD_ASPECT_RATIO)
                .testTag("credit_card_stack")
                .semantics {
                    contentDescription = "Στοίβα καρτών. Χρησιμοποίησε πάνω ή κάτω για αλλαγή κάρτας."
                    customActions = activeCard?.let { card ->
                        listOf(
                            CustomAccessibilityAction("Προηγούμενη κάρτα") { rotateFrontToBack(-1); true },
                            CustomAccessibilityAction("Επόμενη κάρτα") { rotateFrontToBack(1); true },
                            CustomAccessibilityAction("Άνοιγμα λεπτομερειών") { onOpenCard(card.id); true },
                        )
                    }.orEmpty()
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || deleteArmedId != null) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> { rotateFrontToBack(-1); true }
                        Key.DirectionDown -> { rotateFrontToBack(1); true }
                        Key.Enter, Key.Spacebar -> activeCard?.let { onOpenCard(it.id) }.let { true }
                        else -> false
                    }
                }
                .focusable(),
        ) {
            val maxVisibleHeight = maxHeight
            orderedCards.forEachIndexed { index, card ->
                key(card.id) {
                    val isTop = index == 0
                    val layout = stackLayouts.getOrElse(index) {
                        StackLayout(42.dp, .89f, 0f, 0f, (26 - index).toFloat())
                    }
                    val targetY by animateFloatAsState(
                        targetValue = with(density) { layout.y.toPx() },
                        animationSpec = tween(if (reducedMotion) 0 else 480),
                        label = "card-stack-y",
                    )
                    val targetScale by animateFloatAsState(
                        targetValue = layout.scale,
                        animationSpec = tween(if (reducedMotion) 0 else 480),
                        label = "card-stack-scale",
                    )
                    val targetAlpha by animateFloatAsState(
                        targetValue = if (index < VISIBLE_STACK_LAYERS) layout.alpha else 0f,
                        animationSpec = tween(if (reducedMotion) 0 else 340),
                        label = "card-stack-alpha",
                    )
                    val dragY = if (isTop) {
                        if (dragging) dragOffset else dragSettle.value
                    } else {
                        0f
                    }
                    var tiltX by remember(card.id) { mutableFloatStateOf(0f) }
                    var tiltY by remember(card.id) { mutableFloatStateOf(0f) }
                    val layerModifier = Modifier
                        .fillMaxSize()
                        .zIndex(layout.z)
                        .graphicsLayer {
                            translationY = targetY + dragY
                            scaleX = targetScale
                            scaleY = targetScale
                            rotationZ = layout.rotation
                            rotationX = if (isTop) tiltX else 0f
                            rotationY = if (isTop) tiltY else 0f
                            alpha = targetAlpha
                            cameraDistance = 18f * density.density
                        }
                        .then(
                            if (isTop && deleteArmedId == null && deletingId == null) {
                                Modifier.pointerInput(card.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            dragging = true
                                            scope.launch { dragSettle.stop() }
                                        },
                                        onDragEnd = ::finishDrag,
                                        onDragCancel = ::finishDrag,
                                    ) { change, amount ->
                                        change.consume()
                                        dragOffset = (dragOffset + amount.y).coerceIn(-124.dp.toPx(), 124.dp.toPx())
                                    }
                                }
                            } else Modifier
                        )
                        .then(
                            if (isTop) {
                                Modifier.pointerInput(card.id, deleteArmedId) {
                                    val width = size.width.coerceAtLeast(1).toFloat()
                                    val height = size.height.coerceAtLeast(1).toFloat()
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull() ?: continue
                                            if (event.type == PointerEventType.Exit) {
                                                tiltX = 0f
                                                tiltY = 0f
                                            } else if (change.type == PointerType.Mouse && deleteArmedId == null) {
                                                val nx = ((change.position.x / width) - .5f) * 2f
                                                val ny = ((change.position.y / height) - .5f) * 2f
                                                tiltY = (nx * 6.5f).coerceIn(-6.5f, 6.5f)
                                                tiltX = (-ny * 6.5f).coerceIn(-6.5f, 6.5f)
                                            }
                                        }
                                    }
                                }
                            } else Modifier
                        )

                    CreditCardFace(
                        card = card,
                        visual = remember(card.id, card.nickname) { visualFor(card) },
                        revealed = revealed?.takeIf { isTop },
                        loading = loading && isTop,
                        isTop = isTop,
                        deleteArmed = deleteArmedId == card.id,
                        deleteProgress = deleteProgress,
                        deleting = deletingId == card.id,
                        reducedMotion = reducedMotion,
                        onReveal = {
                            if (revealed != null) onHideSecrets() else onRevealSecrets()
                        },
                        onCopyNumber = { copySecret("Ο αριθμός", revealed?.pan) },
                        onCopyExpiry = { copySecret("Η λήξη", revealed?.expiry) },
                        onCopyCvv = { copySecret("Το CVV", revealed?.cvv) },
                        onOpen = { onOpenCard(card.id) },
                        onDeleteRequested = {
                            deleteArmedId = card.id
                            deleteProgress = 0f
                        },
                        onDeleteCancelled = {
                            deleteArmedId = null
                            deleteProgress = 0f
                        },
                        onDeleteProgress = { deleteProgress = it.coerceIn(0f, 1f) },
                        onDeleteCommitted = { commitDelete(card.id) },
                        modifier = layerModifier,
                    )
                }
            }

            if (orderedCards.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Δεν υπάρχουν κάρτες",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("credit_card_stack_dots"),
        ) {
            orderedCards.forEachIndexed { index, card ->
                Box(
                    modifier = Modifier
                        .width(if (index == 0) 22.dp else 6.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (index == 0) Color(0xFF4777D6) else Color(0xFFBDC9DB))
                        .testTag("credit_card_dot_${card.id}"),
                )
            }
        }

        Text(
            text = statusMessage,
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun CreditCardFace(
    card: MoneyCard,
    visual: CardVisual,
    revealed: CardSecretUiState.Revealed?,
    loading: Boolean,
    isTop: Boolean,
    deleteArmed: Boolean,
    deleteProgress: Float,
    deleting: Boolean,
    reducedMotion: Boolean,
    onReveal: () -> Unit,
    onCopyNumber: () -> Unit,
    onCopyExpiry: () -> Unit,
    onCopyCvv: () -> Unit,
    onOpen: () -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteCancelled: () -> Unit,
    onDeleteProgress: (Float) -> Unit,
    onDeleteCommitted: () -> Unit,
    modifier: Modifier,
) {
    val brush = remember(visual.templateId) { Brush.linearGradient(visual.colors) }
    val deletePhase by animateFloatAsState(
        targetValue = if (deleting) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 580),
        label = "card-delete-collapse",
    )
    val cardShape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .shadow(17.dp, cardShape, ambientColor = Color.Black.copy(alpha = .18f), spotColor = Color.Black.copy(alpha = .16f))
            .clip(cardShape)
            .background(brush)
            .graphicsLayer {
                scaleX = 1f - deletePhase * .10f
                scaleY = 1f - deletePhase * .10f
                translationY = deletePhase * 22.dp.toPx()
                alpha = 1f - deletePhase
            }
            .then(
                if (isTop && !deleteArmed) {
                    Modifier.clickable(role = Role.Button, onClick = onOpen)
                } else Modifier
            )
            .testTag("credit_card_${card.id}"),
    ) {
        CardSurfaceDecoration(visual)

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 21.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BrandMark(visual)
                    Text(
                        text = card.nickname,
                        color = visual.muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isTop) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CardIconButton(
                            label = if (revealed == null) "Εμφάνιση στοιχείων" else "Απόκρυψη στοιχείων",
                            enabled = !loading,
                            onClick = onReveal,
                        ) {
                            if (loading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = visual.text)
                            } else {
                                EyeGlyph(hidden = revealed == null, color = visual.text)
                            }
                        }
                        CardIconButton(label = "Διαγραφή κάρτας", onClick = onDeleteRequested) {
                            TrashGlyph(visual.text)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SecretLine(
                    text = revealed?.pan ?: maskedNumber(card.last4),
                    color = visual.text,
                    copyLabel = "Αντιγραφή αριθμού",
                    copyEnabled = revealed?.pan != null,
                    onCopy = onCopyNumber,
                    large = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    SecretField(
                        label = "VALID THRU",
                        value = revealed?.expiry ?: "••/••",
                        color = visual.text,
                        muted = visual.muted,
                        copyLabel = "Αντιγραφή λήξης",
                        copyEnabled = revealed?.expiry != null,
                        onCopy = onCopyExpiry,
                        modifier = Modifier.weight(1f),
                    )
                    SecretField(
                        label = "CVV",
                        value = revealed?.cvv ?: "•••",
                        color = visual.text,
                        muted = visual.muted,
                        copyLabel = "Αντιγραφή CVV",
                        copyEnabled = revealed?.cvv != null,
                        onCopy = onCopyCvv,
                        modifier = Modifier.weight(1f),
                    )
                    NetworkMark(card.network, card.kind, visual.text, visual.muted)
                }
            }
        }

        if (deleteArmed && isTop) {
            DeleteConfirmation(
                progress = deleteProgress,
                color = visual.text,
                onCancel = onDeleteCancelled,
                onProgress = onDeleteProgress,
                onCommit = onDeleteCommitted,
            )
        }

        if (deleting && !reducedMotion) {
            ShredSlices(brush = brush, phase = deletePhase, shape = cardShape)
            Box(modifier = Modifier.fillMaxSize().background(Color(0x55FF607A).copy(alpha = (1f - deletePhase) * .34f)))
        }
    }
}

@Composable
private fun CardSurfaceDecoration(visual: CardVisual) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                listOf(Color.White.copy(alpha = .18f), Color.Transparent, Color.White.copy(alpha = .08f)),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
        if (visual.templateId.contains("metal") || visual.templateId.contains("platinum") || visual.templateId == "viva-carbon") {
            var x = 0f
            while (x < size.width) {
                drawLine(Color.White.copy(alpha = .035f), Offset(x, 0f), Offset(x, size.height), 1f)
                x += 6f
            }
        }
    }
}

@Composable
private fun BrandMark(visual: CardVisual) {
    when {
        visual.templateId.startsWith("piraeus") -> Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(width = 22.dp, height = 24.dp)) {
                repeat(3) { index ->
                    val x = 4f + index * 6f
                    drawLine(
                        color = visual.text,
                        start = Offset(x + 3f, 2f),
                        end = Offset(x - 2f, size.height - 2f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Text("Piraeus", color = visual.text, fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
        }
        visual.templateId.startsWith("revolut") -> Text("Revolut", color = visual.text, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        visual.templateId.startsWith("alpha") -> Column {
            Text("ALPHA BANK", color = visual.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
            Text(if (visual.templateId == "alpha") "enter" else "bonus", color = visual.text, fontSize = 14.sp)
        }
        visual.templateId.startsWith("payzy") -> Text("payzy", color = visual.text, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
        visual.templateId.startsWith("viva") -> Text("Viva Wallet", color = visual.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        else -> Text(visual.brandLabel, color = visual.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecretLine(
    text: String,
    color: Color,
    copyLabel: String,
    copyEnabled: Boolean,
    onCopy: () -> Unit,
    large: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = text,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = if (large) 20.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = if (large) 1.5.sp else 1.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        CardIconButton(label = copyLabel, enabled = copyEnabled, compact = true, onClick = onCopy) {
            CopyGlyph(color.copy(alpha = if (copyEnabled) 1f else .45f))
        }
    }
}

@Composable
private fun SecretField(
    label: String,
    value: String,
    color: Color,
    muted: Color,
    copyLabel: String,
    copyEnabled: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = muted, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            CardIconButton(label = copyLabel, enabled = copyEnabled, compact = true, onClick = onCopy) {
                CopyGlyph(color.copy(alpha = if (copyEnabled) 1f else .45f))
            }
        }
    }
}

@Composable
private fun NetworkMark(network: String, kind: String, color: Color, muted: Color) {
    Column(horizontalAlignment = Alignment.End) {
        if (network.equals("MASTERCARD", true) || network.equals("Mastercard", true)) {
            Canvas(modifier = Modifier.size(width = 38.dp, height = 22.dp)) {
                drawCircle(Color(0xFFEB001B), radius = size.height / 2f, center = Offset(size.height / 2f, size.height / 2f))
                drawCircle(Color(0xFFF79E1B).copy(alpha = .92f), radius = size.height / 2f, center = Offset(size.width - size.height / 2f, size.height / 2f))
            }
            Text("mastercard", color = color, fontSize = 7.sp)
        } else {
            Text("VISA", color = color, fontSize = 18.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
        }
        Text(kind, color = muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CardIconButton(
    label: String,
    enabled: Boolean = true,
    compact: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(if (compact) 7.dp else 10.dp),
        color = Color.White.copy(alpha = if (enabled) .13f else .06f),
        modifier = Modifier
            .size(if (compact) 24.dp else 32.dp)
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun DeleteConfirmation(
    progress: Float,
    color: Color,
    onCancel: () -> Unit,
    onProgress: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC1F1621))
            .padding(14.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Διαγραφή κάρτας;", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "Σύρε το κόκκινο χειριστήριο μέχρι τέρμα δεξιά. Η ενέργεια δεν αναιρείται.",
                    color = Color.White.copy(alpha = .76f),
                    fontSize = 10.sp,
                )
            }
            CardIconButton(label = "Ακύρωση", onClick = onCancel) { CloseGlyph(Color.White) }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Color.Black.copy(alpha = .38f))
                .focusRequester(focusRequester)
                .semantics {
                    role = Role.Button
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f, 9)
                    setProgress { requested -> onProgress(requested.coerceIn(0f, 1f)); true }
                    contentDescription = "Σύρε για οριστική διαγραφή"
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionRight, Key.DirectionUp -> { onProgress((progress + .10f).coerceAtMost(1f)); true }
                        Key.DirectionLeft, Key.DirectionDown -> { onProgress((progress - .10f).coerceAtLeast(0f)); true }
                        Key.MoveHome -> { onProgress(0f); true }
                        Key.MoveEnd -> { onProgress(1f); true }
                        Key.Enter, Key.Spacebar -> {
                            if (progress >= DELETE_COMMIT_THRESHOLD) onCommit()
                            true
                        }
                        Key.Escape -> { onCancel(); true }
                        else -> false
                    }
                }
                .focusable()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            if (progress >= DELETE_COMMIT_THRESHOLD) onCommit() else onProgress(0f)
                        },
                        onDragCancel = { onProgress(0f) },
                    ) { change, amount ->
                        change.consume()
                        val width = size.width.coerceAtLeast(1).toFloat()
                        onProgress((progress + amount.x / width).coerceIn(0f, 1f))
                    }
                }
                .testTag("card_delete_slider"),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceAtLeast(.01f))
                    .padding(5.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0x55FF5469), Color(0xCCFF3552)))),
            )
            Text(
                "ΣΥΡΕ ΓΙΑ ΔΙΑΓΡΑΦΗ",
                color = Color.White.copy(alpha = .82f - progress * .35f),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.Center),
            )
            Surface(
                shape = RoundedCornerShape(13.dp),
                color = Color(0xFFF5F5F7),
                shadowElevation = 7.dp,
                modifier = Modifier
                    .padding(5.dp)
                    .size(48.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = ((progress * 210f).coerceAtLeast(0f)).dp),
            ) {
                Box(contentAlignment = Alignment.Center) { TrashGlyph(Color(0xFFD82D49)) }
            }
        }
    }
}

@Composable
private fun ShredSlices(brush: Brush, phase: Float, shape: RoundedCornerShape) {
    Column(modifier = Modifier.fillMaxSize().clip(shape)) {
        repeat(9) { index ->
            val side = if (index % 2 == 0) -1f else 1f
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = side * phase * (44f + index * 5f)
                        translationY = phase * (20f + (index - 4) * 3f)
                        rotationZ = side * phase * (4f + index * .55f)
                        alpha = 1f - phase
                    }
                    .background(brush),
            )
        }
    }
}

@Composable
private fun EyeGlyph(hidden: Boolean, color: Color) {
    Canvas(Modifier.size(17.dp)) {
        drawOval(color, style = Stroke(width = 1.8f), topLeft = Offset(1f, size.height * .24f), size = Size(size.width - 2f, size.height * .52f))
        drawCircle(color, radius = size.minDimension * .12f, center = center)
        if (!hidden) {
            drawLine(color, Offset(1f, 1f), Offset(size.width - 1f, size.height - 1f), strokeWidth = 1.8f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun CopyGlyph(color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val stroke = Stroke(width = 1.7f)
        drawRoundRect(color, Offset(size.width * .32f, size.height * .32f), Size(size.width * .58f, size.height * .58f), style = stroke)
        drawRoundRect(color, Offset(size.width * .08f, size.height * .08f), Size(size.width * .58f, size.height * .58f), style = stroke)
    }
}

@Composable
private fun TrashGlyph(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val stroke = 1.8f
        drawLine(color, Offset(size.width * .2f, size.height * .3f), Offset(size.width * .8f, size.height * .3f), stroke)
        drawLine(color, Offset(size.width * .36f, size.height * .19f), Offset(size.width * .64f, size.height * .19f), stroke)
        drawRect(color, Offset(size.width * .28f, size.height * .36f), Size(size.width * .44f, size.height * .48f), style = Stroke(stroke))
    }
}

@Composable
private fun CloseGlyph(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        drawLine(color, Offset(3f, 3f), Offset(size.width - 3f, size.height - 3f), 2f, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width - 3f, 3f), Offset(3f, size.height - 3f), 2f, cap = StrokeCap.Round)
    }
}

private fun maskedNumber(last4: String): String {
    val digits = last4.filter(Char::isDigit).takeLast(4).padStart(4, '•')
    return "•••• •••• •••• $digits"
}

private fun visualFor(card: MoneyCard): CardVisual {
    val searchable = "${card.id} ${card.nickname}".lowercase()
    val family = when {
        "piraeus" in searchable || "πειρ" in searchable -> "Piraeus" to piraeusTemplates
        "revolut" in searchable -> "Revolut" to revolutTemplates
        "alpha" in searchable -> "ALPHA BANK" to alphaTemplates
        "payzy" in searchable -> "payzy" to payzyTemplates
        "viva" in searchable -> "Viva Wallet" to vivaTemplates
        else -> null
    }
    if (family != null) {
        val (brand, variants) = family
        val variant = variants[stableHash(card.id).absoluteValue % variants.size]
        val muted = variant.third.copy(alpha = .66f)
        return CardVisual(variant.first, brand, variant.second, variant.third, muted)
    }

    val hue = (stableHash(card.id).absoluteValue % 360).toFloat()
    val first = Color.hsv(hue, .68f, .72f)
    val second = Color.hsv((hue + 28f) % 360f, .62f, .52f)
    return CardVisual("custom", card.nickname.ifBlank { "Card" }, listOf(first, second), Color.White, Color.White.copy(alpha = .67f))
}

private fun stableHash(value: String): Int {
    var hash = 0
    value.forEach { character -> hash = hash * 31 + character.code }
    return if (hash == Int.MIN_VALUE) 0 else hash
}
