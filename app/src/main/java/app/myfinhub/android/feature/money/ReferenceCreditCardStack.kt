package app.myfinhub.android.feature.money

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import app.myfinhub.android.R
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REFERENCE_CARD_ASPECT_RATIO = 1.586f
private const val DELETE_THRESHOLD = .90f
private const val MAX_VISIBLE_LAYERS = 4

private data class ReferenceStackLayout(
    val y: Dp,
    val scale: Float,
    val alpha: Float,
    val rotation: Float,
    val z: Float,
)

private val referenceStackLayouts = listOf(
    ReferenceStackLayout(0.dp, 1f, 1f, 0f, 30f),
    ReferenceStackLayout(14.dp, .972f, .99f, -.5f, 29f),
    ReferenceStackLayout(26.dp, .944f, .95f, .5f, 28f),
    ReferenceStackLayout(37.dp, .916f, .89f, 0f, 27f),
)

private enum class ReferenceBrand { PIRAEUS, REVOLUT, ALPHA, PAYZY, VIVA, CUSTOM }

private data class ReferenceCardVisual(
    val templateId: String,
    val brand: ReferenceBrand,
    val label: String,
    val colors: List<Color>,
    val text: Color,
    val muted: Color = text.copy(alpha = .66f),
)

private fun referenceVisual(
    templateId: String,
    brand: ReferenceBrand,
    label: String,
    colors: List<Color>,
    text: Color,
) = ReferenceCardVisual(templateId, brand, label, colors, text)

private val piraeusVisuals = listOf(
    referenceVisual("piraeus-yellow", ReferenceBrand.PIRAEUS, "Piraeus", listOf(Color(0xFFFFE000), Color(0xFFF3C900)), Color(0xFF0B3852)),
    referenceVisual("piraeus-virtual", ReferenceBrand.PIRAEUS, "Piraeus", listOf(Color(0xFFFFE99C), Color(0xFFF4D66D)), Color(0xFF0B3852)),
    referenceVisual("piraeus-green", ReferenceBrand.PIRAEUS, "Piraeus", listOf(Color(0xFF003C3B), Color(0xFF002F39)), Color(0xFFEEE7D5)),
    referenceVisual("piraeus-gold", ReferenceBrand.PIRAEUS, "Piraeus", listOf(Color(0xFFD9BB77), Color(0xFFB68B45)), Color(0xFF0A3D3C)),
    referenceVisual("piraeus-platinum", ReferenceBrand.PIRAEUS, "Piraeus", listOf(Color(0xFFEDF3F7), Color(0xFFBCC9D6)), Color(0xFF1B3850)),
    referenceVisual("piraeus-midnight", ReferenceBrand.PIRAEUS, "Piraeus", listOf(Color(0xFF123D53), Color(0xFF0A2535)), Color(0xFFE7EFE9)),
)

private val revolutVisuals = listOf(
    referenceVisual("revolut", ReferenceBrand.REVOLUT, "Revolut", listOf(Color(0xFF1599D2), Color(0xFF3D54C6), Color(0xFFD22498)), Color.White),
    referenceVisual("revolut-sage", ReferenceBrand.REVOLUT, "Revolut", listOf(Color(0xFF8DA996), Color(0xFF5D796F)), Color(0xFFF7F4ED)),
    referenceVisual("revolut-midnight", ReferenceBrand.REVOLUT, "Revolut", listOf(Color(0xFF202A43), Color(0xFF10172A)), Color(0xFFF6F4ED)),
    referenceVisual("revolut-slate", ReferenceBrand.REVOLUT, "Revolut", listOf(Color(0xFF6F7478), Color(0xFF40464B)), Color(0xFFF8F6F0)),
    referenceVisual("revolut-lilac", ReferenceBrand.REVOLUT, "Revolut", listOf(Color(0xFFB07CFF), Color(0xFF6147C8)), Color.White),
    referenceVisual("revolut-arctic", ReferenceBrand.REVOLUT, "Revolut", listOf(Color(0xFFF7FBFF), Color(0xFFBFD7EA)), Color(0xFF22354A)),
    referenceVisual("revolut-ruby", ReferenceBrand.REVOLUT, "Revolut", listOf(Color(0xFF7A0829), Color(0xFFD1255B)), Color(0xFFFFF8FB)),
    referenceVisual("revolut-emerald", ReferenceBrand.REVOLUT, "Revolut", listOf(Color(0xFF0C7D5C), Color(0xFF0A4F5E)), Color(0xFFF4FFF8)),
    referenceVisual("revolut-metal-black", ReferenceBrand.REVOLUT, "Revolut", listOf(Color(0xFF35373A), Color(0xFF0D0E10)), Color(0xFFF4F3EF)),
    referenceVisual("revolut-metal-gold", ReferenceBrand.REVOLUT, "Revolut", listOf(Color(0xFFD4B76F), Color(0xFF8D6C2D)), Color(0xFF342812)),
    referenceVisual("revolut-metal-bronze", ReferenceBrand.REVOLUT, "Revolut", listOf(Color(0xFF9C6547), Color(0xFF543125)), Color(0xFFFFF6E7)),
    referenceVisual("revolut-metal-silver", ReferenceBrand.REVOLUT, "Revolut", listOf(Color(0xFFE2E6E7), Color(0xFF949DA3)), Color(0xFF283039)),
    referenceVisual("revolut-ultra", ReferenceBrand.REVOLUT, "Revolut", listOf(Color(0xFFF3F2EE), Color(0xFFC6C7C8)), Color(0xFF25262A)),
)

private val alphaVisuals = listOf(
    referenceVisual("alpha", ReferenceBrand.ALPHA, "ALPHA BANK", listOf(Color(0xFF83C4F4), Color(0xFF5DA3DE)), Color.White),
    referenceVisual("alpha-bonus", ReferenceBrand.ALPHA, "ALPHA BANK", listOf(Color(0xFF173F71), Color(0xFF0B274D)), Color(0xFFF6FBFF)),
    referenceVisual("alpha-gold", ReferenceBrand.ALPHA, "ALPHA BANK", listOf(Color(0xFF40351F), Color(0xFF1D1913)), Color(0xFFE7D0A0)),
    referenceVisual("alpha-sky", ReferenceBrand.ALPHA, "ALPHA BANK", listOf(Color(0xFF9AD2F8), Color(0xFF4F93D0)), Color.White),
    referenceVisual("alpha-midnight", ReferenceBrand.ALPHA, "ALPHA BANK", listOf(Color(0xFF183D72), Color(0xFF0A203F)), Color(0xFFEDF6FF)),
)

private val payzyVisuals = listOf(
    referenceVisual("payzy", ReferenceBrand.PAYZY, "payzy", listOf(Color(0xFF6B03E8), Color(0xFF5100C7)), Color.White),
    referenceVisual("payzy-physical", ReferenceBrand.PAYZY, "payzy", listOf(Color(0xFF43208F), Color(0xFF6248D2)), Color.White),
    referenceVisual("payzy-pro", ReferenceBrand.PAYZY, "payzy", listOf(Color(0xFF63CFD0), Color(0xFF46B4BF)), Color.White),
    referenceVisual("payzy-pro-night", ReferenceBrand.PAYZY, "payzy", listOf(Color(0xFF101B39), Color(0xFF060C20)), Color.White),
    referenceVisual("payzy-neo", ReferenceBrand.PAYZY, "payzy", listOf(Color(0xFFFF2AB9), Color(0xFF4F00BE)), Color.White),
)

private val vivaVisuals = listOf(
    referenceVisual("viva", ReferenceBrand.VIVA, "Viva Wallet", listOf(Color(0xFF233458), Color(0xFF121A2F)), Color(0xFFF6F8FD)),
    referenceVisual("viva-employee", ReferenceBrand.VIVA, "Viva Wallet", listOf(Color(0xFF354767), Color(0xFF1A243C)), Color(0xFFF6F8FD)),
    referenceVisual("viva-digital", ReferenceBrand.VIVA, "Viva Wallet", listOf(Color(0xFF0F1B34), Color(0xFF08101F)), Color(0xFFF6F8FD)),
    referenceVisual("viva-signature", ReferenceBrand.VIVA, "Viva Wallet", listOf(Color(0xFF4D2F8A), Color(0xFF22184B)), Color(0xFFF6F8FD)),
    referenceVisual("viva-carbon", ReferenceBrand.VIVA, "Viva Wallet", listOf(Color(0xFF202020), Color(0xFF080B12)), Color(0xFFF6F8FD)),
    referenceVisual("viva-cobalt", ReferenceBrand.VIVA, "Viva Wallet", listOf(Color(0xFF0E5FB0), Color(0xFF113465)), Color(0xFFF6F8FD)),
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
    var locallyRemovedIds by remember { mutableStateOf(emptySet<String>()) }
    var deleteArmedId by remember { mutableStateOf<String?>(null) }
    var deleteProgress by remember { mutableFloatStateOf(0f) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var stackFocused by remember { mutableStateOf(false) }
    val settleOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val reducedMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
    val swipeThreshold = with(density) { 72.dp.toPx() }
    val restackDistance = with(density) { 92.dp.toPx() }

    LaunchedEffect(ids) {
        locallyRemovedIds = locallyRemovedIds.filterTo(mutableSetOf()) { it in ids }
        val available = ids.filterNot(locallyRemovedIds::contains)
        order = order.filter { it in available } + available.filterNot(order::contains)
    }

    val cardById = remember(cards) { cards.associateBy(MoneyCard::id) }
    val orderedCards = order.mapNotNull(cardById::get).filterNot { it.id in locallyRemovedIds }
    val activeCard = orderedCards.firstOrNull()
    val activeId = activeCard?.id

    LaunchedEffect(activeId) {
        deleteArmedId = null
        deleteProgress = 0f
        onActiveCardChanged(activeId)
    }

    val revealed = (secretState as? CardSecretUiState.Revealed)?.takeIf { it.cardId == activeId }
    val loading = (secretState as? CardSecretUiState.Loading)?.cardId == activeId

    fun restack(direction: Int) {
        if (order.size < 2 || deletingId != null || deleteArmedId != null) return
        onHideSecrets()
        val start = dragOffset
        dragOffset = 0f
        dragging = false
        scope.launch {
            settleOffset.snapTo(start)
            if (!reducedMotion) {
                settleOffset.animateTo(
                    direction.coerceIn(-1, 1) * restackDistance,
                    animationSpec = tween(150),
                )
            }
            order = order.drop(1) + order.first()
            settleOffset.snapTo(0f)
        }
    }

    fun settleDrag() {
        val finalOffset = dragOffset
        if (finalOffset.absoluteValue >= swipeThreshold) {
            restack(if (finalOffset < 0f) -1 else 1)
        } else {
            dragOffset = 0f
            dragging = false
            scope.launch {
                settleOffset.snapTo(finalOffset)
                if (reducedMotion) {
                    settleOffset.snapTo(0f)
                } else {
                    settleOffset.animateTo(0f, tween(180))
                }
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
            locallyRemovedIds = locallyRemovedIds + cardId
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
        Box(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxWidth()
                .aspectRatio(REFERENCE_CARD_ASPECT_RATIO)
                .then(
                    if (stackFocused) {
                        Modifier.border(3.dp, Color(0xFF4777D6), RoundedCornerShape(25.dp))
                    } else {
                        Modifier
                    },
                )
                .testTag("credit_card_stack")
                .semantics {
                    contentDescription = "Στοίβα καρτών. Χρησιμοποίησε τα πλήκτρα πάνω και κάτω για αλλαγή κάρτας."
                    customActions = activeCard?.let { card ->
                        listOf(
                            CustomAccessibilityAction("Προηγούμενη κάρτα") { restack(-1); true },
                            CustomAccessibilityAction("Επόμενη κάρτα") { restack(1); true },
                            CustomAccessibilityAction("Άνοιγμα λεπτομερειών") { onOpenCard(card.id); true },
                        )
                    }.orEmpty()
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || deleteArmedId != null) {
                        return@onPreviewKeyEvent false
                    }
                    when (event.key) {
                        Key.DirectionUp -> { restack(-1); true }
                        Key.DirectionDown -> { restack(1); true }
                        Key.Enter, Key.Spacebar -> {
                            activeCard?.let { onOpenCard(it.id) }
                            activeCard != null
                        }
                        else -> false
                    }
                }
                .onFocusChanged { stackFocused = it.isFocused }
                .focusable(),
        ) {
            orderedCards.forEachIndexed { index, card ->
                key(card.id) {
                    val isTop = index == 0
                    val layout = referenceStackLayouts.getOrElse(index) {
                        ReferenceStackLayout(42.dp, .89f, 0f, 0f, (26 - index).toFloat())
                    }
                    val layoutY by animateFloatAsState(
                        targetValue = with(density) { layout.y.toPx() },
                        animationSpec = tween(if (reducedMotion) 0 else 480),
                        label = "reference-card-y",
                    )
                    val layoutScale by animateFloatAsState(
                        targetValue = layout.scale,
                        animationSpec = tween(if (reducedMotion) 0 else 480),
                        label = "reference-card-scale",
                    )
                    val layoutAlpha by animateFloatAsState(
                        targetValue = if (index < MAX_VISIBLE_LAYERS) layout.alpha else 0f,
                        animationSpec = tween(if (reducedMotion) 0 else 340),
                        label = "reference-card-alpha",
                    )
                    val dragY = if (isTop) {
                        if (dragging) dragOffset else settleOffset.value
                    } else {
                        0f
                    }
                    var tiltX by remember(card.id) { mutableFloatStateOf(0f) }
                    var tiltY by remember(card.id) { mutableFloatStateOf(0f) }

                    val layerModifier = Modifier
                        .fillMaxSize()
                        .zIndex(layout.z)
                        .graphicsLayer {
                            translationY = layoutY + dragY
                            scaleX = layoutScale
                            scaleY = layoutScale
                            rotationZ = layout.rotation
                            rotationX = if (isTop) tiltX else 0f
                            rotationY = if (isTop) tiltY else 0f
                            alpha = layoutAlpha
                            cameraDistance = 18f * density.density
                        }
                        .then(
                            if (isTop && deleteArmedId == null && deletingId == null) {
                                Modifier.pointerInput(card.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            dragging = true
                                            scope.launch { settleOffset.stop() }
                                        },
                                        onDragEnd = ::settleDrag,
                                        onDragCancel = ::settleDrag,
                                    ) { change, amount ->
                                        change.consume()
                                        dragOffset = (dragOffset + amount.y)
                                            .coerceIn(-124.dp.toPx(), 124.dp.toPx())
                                    }
                                }
                            } else {
                                Modifier
                            },
                        )
                        .then(
                            if (isTop && !reducedMotion) {
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
                                            } else if (
                                                event.type == PointerEventType.Move &&
                                                change.type == PointerType.Mouse &&
                                                deleteArmedId == null
                                            ) {
                                                val nx = ((change.position.x / width) - .5f) * 2f
                                                val ny = ((change.position.y / height) - .5f) * 2f
                                                tiltY = (nx * 6.5f).coerceIn(-6.5f, 6.5f)
                                                tiltX = (-ny * 6.5f).coerceIn(-6.5f, 6.5f)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            },
                        )

                    ReferenceCardFace(
                        card = card,
                        visual = remember(card.id, card.bankId, card.nickname) { visualForReferenceCard(card) },
                        revealed = revealed?.takeIf { isTop },
                        loading = loading && isTop,
                        isTop = isTop,
                        deleteArmed = deleteArmedId == card.id,
                        deleteProgress = deleteProgress,
                        deleting = deletingId == card.id,
                        reducedMotion = reducedMotion,
                        onReveal = {
                            if (revealed == null) onRevealSecrets() else onHideSecrets()
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Δεν υπάρχουν κάρτες",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("credit_card_stack_empty"),
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
                if (card.id != deletingId) {
                    Box(
                        Modifier
                            .width(if (index == 0) 22.dp else 6.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (index == 0) Color(0xFF4777D6) else Color(0xFFBDC9DB))
                            .testTag("credit_card_dot_${card.id}"),
                    )
                }
            }
        }

        Text(
            statusMessage,
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun ReferenceCardFace(
    card: MoneyCard,
    visual: ReferenceCardVisual,
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
    val collapse by animateFloatAsState(
        targetValue = if (deleting) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 580),
        label = "reference-card-delete",
    )
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .shadow(17.dp, shape, ambientColor = Color.Black.copy(alpha = .18f), spotColor = Color.Black.copy(alpha = .16f))
            .clip(shape)
            .background(brush)
            .graphicsLayer {
                scaleX = 1f - collapse * .10f
                scaleY = 1f - collapse * .10f
                translationY = collapse * 22.dp.toPx()
                alpha = 1f - collapse
            }
            .then(
                if (isTop && !deleteArmed) {
                    Modifier.clickable(role = Role.Button, onClick = onOpen)
                } else {
                    Modifier
                },
            )
            .testTag("credit_card_${card.id}"),
    ) {
        ReferenceSurfaceDecoration(visual)

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 17.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ReferenceBrandMark(visual)
                    Text(
                        card.nickname,
                        color = visual.muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isTop) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        ReferenceActionButton(
                            label = if (revealed == null) "Εμφάνιση στοιχείων" else "Απόκρυψη στοιχείων",
                            enabled = !loading,
                            onClick = onReveal,
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = visual.text,
                                )
                            } else {
                                ReferenceEyeGlyph(hidden = revealed == null, color = visual.text)
                            }
                        }
                        ReferenceActionButton(label = "Διαγραφή κάρτας", onClick = onDeleteRequested) {
                            ReferenceTrashGlyph(visual.text)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ReferenceSecretLine(
                    text = revealed?.pan ?: referenceMaskedPan(card.last4),
                    color = visual.text,
                    copyLabel = "Αντιγραφή αριθμού",
                    copyEnabled = revealed?.pan != null,
                    onCopy = onCopyNumber,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    ReferenceSecretField(
                        label = "VALID THRU",
                        value = revealed?.expiry ?: "••/••",
                        color = visual.text,
                        muted = visual.muted,
                        copyLabel = "Αντιγραφή λήξης",
                        copyEnabled = revealed?.expiry != null,
                        onCopy = onCopyExpiry,
                        modifier = Modifier.weight(1f),
                    )
                    ReferenceSecretField(
                        label = "CVV",
                        value = revealed?.cvv ?: "•••",
                        color = visual.text,
                        muted = visual.muted,
                        copyLabel = "Αντιγραφή CVV",
                        copyEnabled = revealed?.cvv != null,
                        onCopy = onCopyCvv,
                        modifier = Modifier.weight(1f),
                    )
                    ReferenceNetworkMark(card.network, card.kind, visual.text, visual.muted)
                }
            }
        }

        if (deleteArmed && isTop) {
            ReferenceDeleteConfirmation(
                progress = deleteProgress,
                onCancel = onDeleteCancelled,
                onProgress = onDeleteProgress,
                onCommit = onDeleteCommitted,
            )
        }

        if (deleting && !reducedMotion) {
            ReferenceShredSlices(brush, collapse, shape)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFF607A).copy(alpha = (1f - collapse) * .22f)),
            )
        }
    }
}

@Composable
private fun ReferenceSurfaceDecoration(visual: ReferenceCardVisual) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                listOf(Color.White.copy(alpha = .18f), Color.Transparent, Color.White.copy(alpha = .08f)),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
        if (
            visual.templateId.contains("metal") ||
            visual.templateId.contains("platinum") ||
            visual.templateId == "viva-carbon"
        ) {
            var x = 0f
            while (x < size.width) {
                drawLine(Color.White.copy(alpha = .035f), Offset(x, 0f), Offset(x, size.height), 1f)
                x += 6f
            }
        }
    }
}

@Composable
private fun ReferenceBrandMark(visual: ReferenceCardVisual) {
    when (visual.brand) {
        ReferenceBrand.PIRAEUS -> Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(width = 22.dp, height = 24.dp)) {
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
            Spacer(Modifier.width(5.dp))
            Text(
                "Piraeus",
                color = visual.text,
                fontSize = 20.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
            )
        }

        ReferenceBrand.REVOLUT -> Text(
            "Revolut",
            color = visual.text,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
        )

        ReferenceBrand.ALPHA -> Column {
            Text("ALPHA BANK", color = visual.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
            Text(if (visual.templateId == "alpha") "enter" else "bonus", color = visual.text, fontSize = 12.sp)
        }

        ReferenceBrand.PAYZY -> Image(
            painter = painterResource(R.drawable.mfh_payzy_reference_logo),
            contentDescription = "payzy by COSMOTE",
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(27.dp).widthIn(max = 112.dp),
        )

        ReferenceBrand.VIVA -> Image(
            painter = painterResource(R.drawable.mfh_viva_reference_logo),
            contentDescription = "Viva Wallet",
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(23.dp).widthIn(max = 132.dp),
        )

        ReferenceBrand.CUSTOM -> Text(
            visual.label,
            color = visual.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReferenceSecretLine(
    text: String,
    color: Color,
    copyLabel: String,
    copyEnabled: Boolean,
    onCopy: () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val numberFontSize = if (fontScale >= 1.3f) 13.sp else 17.sp
    val numberLetterSpacing = if (fontScale >= 1.3f) .6.sp else 1.2.sp

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = numberFontSize,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = numberLetterSpacing,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        ReferenceActionButton(
            label = copyLabel,
            enabled = copyEnabled,
            compactVisual = true,
            onClick = onCopy,
        ) {
            ReferenceCopyGlyph(color.copy(alpha = if (copyEnabled) 1f else .45f))
        }
    }
}

@Composable
private fun ReferenceSecretField(
    label: String,
    value: String,
    color: Color,
    muted: Color,
    copyLabel: String,
    copyEnabled: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, color = muted, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                color = color,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
            ReferenceActionButton(
                label = copyLabel,
                enabled = copyEnabled,
                compactVisual = true,
                onClick = onCopy,
            ) {
                ReferenceCopyGlyph(color.copy(alpha = if (copyEnabled) 1f else .45f))
            }
        }
    }
}

@Composable
private fun ReferenceNetworkMark(network: String, kind: String, color: Color, muted: Color) {
    Column(horizontalAlignment = Alignment.End) {
        if (network.equals("MASTERCARD", true) || network.equals("Mastercard", true)) {
            Canvas(Modifier.size(width = 38.dp, height = 22.dp)) {
                drawCircle(
                    Color(0xFFEB001B),
                    radius = size.height / 2f,
                    center = Offset(size.height / 2f, size.height / 2f),
                )
                drawCircle(
                    Color(0xFFF79E1B).copy(alpha = .92f),
                    radius = size.height / 2f,
                    center = Offset(size.width - size.height / 2f, size.height / 2f),
                )
            }
            Text("mastercard", color = color, fontSize = 6.sp)
        } else {
            Text(
                "VISA",
                color = color,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
            )
        }
        Text(kind, color = muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReferenceActionButton(
    label: String,
    enabled: Boolean = true,
    compactVisual: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val visualSize = if (compactVisual) 23.dp else 31.dp
    val visualShape = RoundedCornerShape(if (compactVisual) 7.dp else 10.dp)

    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = label }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(visualSize)
                .clip(visualShape)
                .background(Color.White.copy(alpha = if (enabled) .13f else .06f))
                .then(
                    if (focused) Modifier.border(2.dp, Color.White, visualShape) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun ReferenceDeleteConfirmation(
    progress: Float,
    onCancel: () -> Unit,
    onProgress: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val currentProgress by rememberUpdatedState(progress)
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
                    fontSize = 9.sp,
                )
            }
            ReferenceActionButton(label = "Ακύρωση", onClick = onCancel) {
                ReferenceCloseGlyph(Color.White)
            }
        }
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Color.Black.copy(alpha = .38f))
                .focusRequester(focusRequester)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f, 9)
                    setProgress { requested ->
                        onProgress(requested.coerceIn(0f, 1f))
                        true
                    }
                    contentDescription = "Σύρε για οριστική διαγραφή"
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionRight, Key.DirectionUp -> {
                            onProgress((progress + .10f).coerceAtMost(1f)); true
                        }
                        Key.DirectionLeft, Key.DirectionDown -> {
                            onProgress((progress - .10f).coerceAtLeast(0f)); true
                        }
                        Key.MoveHome -> { onProgress(0f); true }
                        Key.MoveEnd -> { onProgress(1f); true }
                        Key.Enter, Key.Spacebar -> {
                            if (progress >= DELETE_THRESHOLD) onCommit()
                            true
                        }
                        Key.Escape -> { onCancel(); true }
                        else -> false
                    }
                }
                .focusable()
                .pointerInput(Unit) {
                    var gestureProgress = currentProgress
                    detectDragGestures(
                        onDragStart = { gestureProgress = currentProgress },
                        onDragEnd = {
                            if (gestureProgress >= DELETE_THRESHOLD) onCommit() else onProgress(0f)
                        },
                        onDragCancel = { onProgress(0f) },
                    ) { change, amount ->
                        change.consume()
                        gestureProgress = (gestureProgress + amount.x / size.width.coerceAtLeast(1).toFloat())
                            .coerceIn(0f, 1f)
                        onProgress(gestureProgress)
                    }
                }
                .testTag("card_delete_slider"),
        ) {
            val thumbTravel = (maxWidth - 58.dp).coerceAtLeast(0.dp)
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceAtLeast(.01f))
                    .padding(5.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0x55FF5469), Color(0xCCFF3552)))),
            )
            Text(
                "ΣΥΡΕ ΓΙΑ ΔΙΑΓΡΑΦΗ",
                color = Color.White.copy(alpha = .82f - progress * .35f),
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = .8.sp,
                modifier = Modifier.align(Alignment.Center),
            )
            Box(
                modifier = Modifier
                    .padding(5.dp)
                    .size(48.dp)
                    .offset(x = thumbTravel * progress)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFFF5F5F7))
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.Center,
            ) {
                ReferenceTrashGlyph(Color(0xFFD82D49))
            }
        }
    }
}

@Composable
private fun ReferenceShredSlices(brush: Brush, phase: Float, shape: RoundedCornerShape) {
    Column(Modifier.fillMaxSize().clip(shape)) {
        repeat(9) { index ->
            val direction = if (index % 2 == 0) -1f else 1f
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = direction * phase * (44f + index * 5f)
                        translationY = phase * (20f + (index - 4) * 3f)
                        rotationZ = direction * phase * (4f + index * .55f)
                        alpha = 1f - phase
                    }
                    .background(brush),
            )
        }
    }
}

@Composable
private fun ReferenceEyeGlyph(hidden: Boolean, color: Color) {
    Canvas(Modifier.size(17.dp)) {
        drawOval(
            color,
            topLeft = Offset(1f, size.height * .24f),
            size = Size(size.width - 2f, size.height * .52f),
            style = Stroke(width = 1.8f),
        )
        drawCircle(color, radius = size.minDimension * .12f, center = center)
        if (!hidden) {
            drawLine(
                color,
                Offset(1f, 1f),
                Offset(size.width - 1f, size.height - 1f),
                strokeWidth = 1.8f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ReferenceCopyGlyph(color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val stroke = Stroke(width = 1.7f)
        drawRoundRect(
            color,
            topLeft = Offset(size.width * .32f, size.height * .32f),
            size = Size(size.width * .58f, size.height * .58f),
            style = stroke,
        )
        drawRoundRect(
            color,
            topLeft = Offset(size.width * .08f, size.height * .08f),
            size = Size(size.width * .58f, size.height * .58f),
            style = stroke,
        )
    }
}

@Composable
private fun ReferenceTrashGlyph(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val stroke = 1.8f
        drawLine(color, Offset(size.width * .2f, size.height * .3f), Offset(size.width * .8f, size.height * .3f), stroke)
        drawLine(color, Offset(size.width * .36f, size.height * .19f), Offset(size.width * .64f, size.height * .19f), stroke)
        drawRect(
            color,
            topLeft = Offset(size.width * .28f, size.height * .36f),
            size = Size(size.width * .44f, size.height * .48f),
            style = Stroke(stroke),
        )
    }
}

@Composable
private fun ReferenceCloseGlyph(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        drawLine(color, Offset(3f, 3f), Offset(size.width - 3f, size.height - 3f), 2f, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width - 3f, 3f), Offset(3f, size.height - 3f), 2f, cap = StrokeCap.Round)
    }
}

private fun referenceMaskedPan(last4: String): String {
    val digits = last4.filter(Char::isDigit).takeLast(4).padStart(4, '•')
    return "•••• •••• •••• $digits"
}

private fun visualForReferenceCard(card: MoneyCard): ReferenceCardVisual {
    val bank = card.bankId.trim().lowercase()
    val searchable = "${card.id} ${card.nickname}".lowercase()
    val variants = when {
        bank == "piraeus" || (bank.isBlank() && ("piraeus" in searchable || "πειρ" in searchable)) -> piraeusVisuals
        bank == "revolut" || (bank.isBlank() && "revolut" in searchable) -> revolutVisuals
        bank == "alpha" || bank == "alpha-bank" || (bank.isBlank() && "alpha" in searchable) -> alphaVisuals
        bank == "payzy" || (bank.isBlank() && "payzy" in searchable) -> payzyVisuals
        bank == "viva" || bank == "viva-wallet" || (bank.isBlank() && "viva" in searchable) -> vivaVisuals
        else -> null
    }
    if (variants != null) {
        return variants[stableReferenceHash(card.id).absoluteValue % variants.size]
    }

    val hue = (stableReferenceHash(card.id).absoluteValue % 360).toFloat()
    return ReferenceCardVisual(
        templateId = "custom",
        brand = ReferenceBrand.CUSTOM,
        label = card.nickname.ifBlank { card.bankId.ifBlank { "Card" } },
        colors = listOf(
            Color.hsv(hue, .68f, .72f),
            Color.hsv((hue + 28f) % 360f, .62f, .52f),
        ),
        text = Color.White,
    )
}

private fun stableReferenceHash(value: String): Int {
    var hash = 0
    value.forEach { character -> hash = hash * 31 + character.code }
    return if (hash == Int.MIN_VALUE) 0 else hash
}
