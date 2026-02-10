package com.diceapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random
import java.util.UUID
import androidx.compose.ui.text.input.KeyboardCapitalization

data class CustomList(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val items: List<String>,
    val color: Color = listColors.random()
)

val listColors = listOf(
    Color(0xFFe94560),
    Color(0xFF00b894),
    Color(0xFF0984e3),
    Color(0xFFfdcb6e),
    Color(0xFF6c5ce7),
    Color(0xFFe17055),
    Color(0xFF00cec9),
    Color(0xFFfd79a8)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainApp()
        }
    }
}

fun saveLists(context: Context, lists: List<CustomList>) {
    val prefs = context.getSharedPreferences("dice_app", Context.MODE_PRIVATE)
    val jsonArray = JSONArray()
    lists.forEach { list ->
        val jsonObject = JSONObject()
        jsonObject.put("id", list.id)
        jsonObject.put("name", list.name)
        jsonObject.put("items", JSONArray(list.items))
        jsonObject.put("color", list.color.value.toLong())
        jsonArray.put(jsonObject)
    }
    prefs.edit().putString("custom_lists", jsonArray.toString()).apply()
}

fun loadLists(context: Context): List<CustomList> {
    val prefs = context.getSharedPreferences("dice_app", Context.MODE_PRIVATE)
    val jsonString = prefs.getString("custom_lists", null) ?: return emptyList()
    val jsonArray = JSONArray(jsonString)
    val lists = mutableListOf<CustomList>()
    for (i in 0 until jsonArray.length()) {
        val jsonObject = jsonArray.getJSONObject(i)
        val itemsArray = jsonObject.getJSONArray("items")
        val items = mutableListOf<String>()
        for (j in 0 until itemsArray.length()) {
            items.add(itemsArray.getString(j))
        }
        lists.add(
            CustomList(
                id = jsonObject.getString("id"),
                name = jsonObject.getString("name"),
                items = items,
                color = Color(jsonObject.getLong("color").toULong())
            )
        )
    }
    return lists
}

@Composable
fun MainApp() {
    val context = LocalContext.current
    val customLists = remember { mutableStateListOf<CustomList>().also { it.addAll(loadLists(context)) } }
    
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1a1a2e),
            Color(0xFF16213e),
            Color(0xFF0f3460)
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(48.dp))
            
            ListsScreen(
                lists = customLists,
                onListsChanged = { 
                    saveLists(context, customLists)
                }
            )
        }
    }
}

@Composable
fun ListsScreen(
    lists: MutableList<CustomList>,
    onListsChanged: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedList by remember { mutableStateOf<CustomList?>(null) }
    var randomResult by remember { mutableStateOf<String?>(null) }
    var isRolling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    
    fun rollList(list: CustomList) {
        if (isRolling || list.items.isEmpty()) return
        
        scope.launch {
            isRolling = true
            
            launch {
                scale.animateTo(0.9f, animationSpec = tween(150))
                scale.animateTo(1.05f, animationSpec = tween(150))
                scale.animateTo(1f, animationSpec = tween(150))
            }
            
            repeat(12) {
                randomResult = list.items.random()
                delay(70)
            }
            
            randomResult = list.items.random()
            isRolling = false
        }
    }
    
    if (showCreateDialog) {
        CreateListDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, items ->
                lists.add(CustomList(name = name, items = items))
                onListsChanged()
                showCreateDialog = false
            }
        )
    }
    
    if (selectedList != null) {
        ListDetailDialog(
            list = selectedList!!,
            randomResult = randomResult,
            isRolling = isRolling,
            scale = scale.value,
            onRoll = { rollList(selectedList!!) },
            onDismiss = { 
                selectedList = null
                randomResult = null
            },
            onDelete = {
                lists.remove(selectedList)
                onListsChanged()
                selectedList = null
                randomResult = null
            },
            onEdit = { updatedList ->
                val index = lists.indexOfFirst { it.id == updatedList.id }
                if (index != -1) {
                    lists[index] = updatedList
                    onListsChanged()
                }
                selectedList = updatedList
            }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.subtitle),
                    fontSize = 13.sp,
                    color = Color(0xFF8b8b8b)
                )
            }
            
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFe94560), Color(0xFFff6b8a))
                        )
                    )
                    .clickable { showCreateDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (lists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2d2d44)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📝",
                            fontSize = 48.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.empty_state_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.empty_state_description),
                        fontSize = 14.sp,
                        color = Color(0xFF6b6b6b),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(lists) { list ->
                    ListCard(
                        list = list,
                        onClick = { selectedList = list }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
fun ListCard(
    list: CustomList,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF252538))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = list.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(list.color.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.items_count, list.items.size),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = list.color
                        )
                    }
                }
                
                if (list.items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        list.items.take(2).forEach { item ->
                            Text(
                                text = "• $item",
                                fontSize = 13.sp,
                                color = Color(0xFF9b9b9b),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (list.items.size > 2) {
                            Text(
                                text = stringResource(R.string.more_items, list.items.size - 2),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = list.color.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(list.color, list.color.copy(alpha = 0.7f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎲",
                    fontSize = 28.sp
                )
            }
        }
    }
}

@Composable
fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFFe94560)
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1a1a2e))
            .padding(16.dp)
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                fontSize = 16.sp,
                color = Color(0xFF5a5a6a)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 16.sp
            ),
            cursorBrush = SolidColor(accentColor),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
    }
}

@Composable
fun CreateListDialog(
    onDismiss: () -> Unit,
    onCreate: (String, List<String>) -> Unit
) {
    var listName by remember { mutableStateOf("") }
    var currentItem by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf<String>() }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val canScrollDown by remember { derivedStateOf { listState.canScrollForward } }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF252538))
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.new_list_title),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = stringResource(R.string.label_name),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6b6b7b),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                StyledTextField(
                    value = listName,
                    onValueChange = { listName = it },
                    placeholder = stringResource(R.string.placeholder_list_name),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.label_elements),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6b6b7b),
                        letterSpacing = 1.sp
                    )
                    if (items.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFe94560).copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${items.size}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFe94560)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1a1a2e))
                            .padding(16.dp)
                    ) {
                        if (currentItem.isEmpty()) {
                            Text(
                                text = stringResource(R.string.placeholder_write_element),
                                fontSize = 16.sp,
                                color = Color(0xFF5a5a6a)
                            )
                        }
                        BasicTextField(
                            value = currentItem,
                            onValueChange = { currentItem = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 16.sp
                            ),
                            cursorBrush = SolidColor(Color(0xFFe94560)),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (currentItem.isNotBlank()) {
                                        items.add(currentItem.trim())
                                        currentItem = ""
                                    }
                                }
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    
                    val addButtonBg = if (currentItem.isNotBlank()) 
                        Brush.linearGradient(listOf(Color(0xFF00b894), Color(0xFF00d9a5)))
                    else Brush.linearGradient(listOf(Color(0xFF3a3a4a), Color(0xFF3a3a4a)))
                    
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(addButtonBg)
                            .clickable {
                                if (currentItem.isNotBlank()) {
                                    items.add(currentItem.trim())
                                    currentItem = ""
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Light,
                            color = Color.White
                        )
                    }
                }
                
                if (items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.heightIn(max = 160.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(items) { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF1a1a2e))
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFe94560).copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFe94560)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = item,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFe94560).copy(alpha = 0.15f))
                                            .clickable { items.remove(item) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "×",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFe94560)
                                        )
                                    }
                                }
                            }
                        }
                        if (canScrollDown) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color(0xFF252538))
                                        )
                                    )
                            )
                            Text(
                                text = stringResource(R.string.scroll_down_hint),
                                fontSize = 11.sp,
                                color = Color(0xFF6b6b7b),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 4.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(28.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3a3a4a)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text(stringResource(R.string.button_cancel), color = Color.White, fontSize = 15.sp)
                    }
                    Button(
                        onClick = { 
                            if (listName.isNotBlank() && items.isNotEmpty()) {
                                onCreate(listName.trim(), items.toList())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (listName.isNotBlank() && items.isNotEmpty()) 
                                Color(0xFFe94560) else Color(0xFF3a3a4a)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text(
                            stringResource(R.string.button_create_list),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmDialog(
    listName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF252538))
                .padding(28.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFe94560).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🗑️",
                        fontSize = 36.sp
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.delete_list_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.delete_list_message, listName),
                    fontSize = 15.sp,
                    color = Color(0xFF8b8b8b),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3a3a4a)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text(stringResource(R.string.button_cancel), color = Color.White, fontSize = 15.sp)
                    }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFe94560)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text(stringResource(R.string.button_delete), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun ListDetailDialog(
    list: CustomList,
    randomResult: String?,
    isRolling: Boolean,
    scale: Float,
    onRoll: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (CustomList) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(list.name) }
    var currentItem by remember { mutableStateOf("") }
    val editItems = remember { mutableStateListOf<String>().also { it.addAll(list.items) } }
    val focusManager = LocalFocusManager.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val canScrollDown by remember { derivedStateOf { listState.canScrollForward } }
    
    fun saveChangesAndClose() {
        if (editName.isNotBlank() && (editName != list.name || editItems.toList() != list.items)) {
            onEdit(list.copy(name = editName.trim(), items = editItems.toList()))
        }
        onDismiss()
    }
    
    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            listName = list.name,
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirm = false }
        )
    }
    
    Dialog(
        onDismissRequest = { saveChangesAndClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF252538))
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEditing) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1a1a2e))
                                .padding(14.dp)
                        ) {
                            BasicTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                cursorBrush = SolidColor(list.color),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                            )
                        }
                    } else {
                        Text(
                            text = list.name,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isEditing) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(listOf(Color(0xFF00b894), Color(0xFF00d9a5)))
                                    )
                                    .clickable {
                                        if (editName.isNotBlank()) {
                                            onEdit(list.copy(name = editName.trim(), items = editItems.toList()))
                                        }
                                        isEditing = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.button_save),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF3a3a4a))
                                    .clickable { isEditing = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "✏️", fontSize = 20.sp)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFe94560).copy(alpha = 0.15f))
                                .clickable { showDeleteConfirm = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🗑️", fontSize = 20.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (!isEditing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(scale)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        list.color.copy(alpha = 0.2f),
                                        list.color.copy(alpha = 0.05f)
                                    )
                                )
                            )
                            .border(
                                width = 2.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        list.color.copy(alpha = 0.5f),
                                        list.color.copy(alpha = 0.2f)
                                    )
                                ),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .clickable(onClick = onRoll)
                            .padding(vertical = 36.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (randomResult != null) {
                                Text(
                                    text = stringResource(R.string.label_result),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = list.color.copy(alpha = 0.6f),
                                    letterSpacing = 3.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = randomResult,
                                    fontSize = when {
                                        randomResult.length > 25 -> 20.sp
                                        randomResult.length > 15 -> 26.sp
                                        else -> 34.sp
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = list.color,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 42.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "✨",
                                    fontSize = 28.sp
                                )
                            } else {
                                Text(
                                    text = "🎲",
                                    fontSize = 48.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.tap_to_choose),
                                    fontSize = 16.sp,
                                    color = Color(0xFF6b6b6b)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Button(
                        onClick = onRoll,
                        enabled = !isRolling && list.items.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = list.color,
                            disabledContainerColor = Color(0xFF3a3a4a)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(
                            text = if (isRolling) stringResource(R.string.rolling) else stringResource(R.string.roll_random),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.label_elements),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6b6b7b),
                        letterSpacing = 1.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(list.color.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${if (isEditing) editItems.size else list.items.size}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = list.color
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                if (isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1a1a2e))
                                .padding(14.dp)
                        ) {
                            if (currentItem.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.placeholder_new_element),
                                    fontSize = 15.sp,
                                    color = Color(0xFF5a5a6a)
                                )
                            }
                            BasicTextField(
                                value = currentItem,
                                onValueChange = { currentItem = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                                cursorBrush = SolidColor(list.color),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (currentItem.isNotBlank()) {
                                            editItems.add(currentItem.trim())
                                            currentItem = ""
                                        }
                                    }
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        
                        val editAddButtonBg = if (currentItem.isNotBlank())
                            Brush.linearGradient(listOf(Color(0xFF00b894), Color(0xFF00d9a5)))
                        else Brush.linearGradient(listOf(Color(0xFF3a3a4a), Color(0xFF3a3a4a)))
                        
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(editAddButtonBg)
                                .clickable {
                                    if (currentItem.isNotBlank()) {
                                        editItems.add(currentItem.trim())
                                        currentItem = ""
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "+", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Light)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                Box {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val displayItems = if (isEditing) editItems else list.items
                        itemsIndexed(displayItems) { index, item ->
                            val isSelected = item == randomResult && !isEditing
                            val bgColor by animateColorAsState(
                                if (isSelected) list.color.copy(alpha = 0.25f) else Color(0xFF1a1a2e),
                                label = "item_bg"
                            )
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bgColor)
                                    .then(
                                        if (isSelected)
                                            Modifier.border(2.dp, list.color, RoundedCornerShape(12.dp))
                                        else Modifier
                                    )
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(
                                                if (isSelected) list.color
                                                else list.color.copy(alpha = 0.2f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else list.color
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = item,
                                        color = if (isSelected) list.color else Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isEditing) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFe94560).copy(alpha = 0.15f))
                                            .clickable { editItems.remove(item) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "×",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFe94560)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (canScrollDown) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(36.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color(0xFF252538))
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}
