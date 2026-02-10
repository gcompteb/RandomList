package com.randomlist

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random
import java.util.UUID
import androidx.compose.ui.text.input.KeyboardCapitalization

data class ListSection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val items: List<String>
)

data class CustomList(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val items: List<String> = emptyList(),
    val sections: List<ListSection> = emptyList(),
    val color: Color = listColors.random()
) {
    val hasSections: Boolean get() = sections.isNotEmpty()
}

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

fun saveListToFirestore(list: CustomList) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val db = FirebaseFirestore.getInstance()
    
    val sectionsData = list.sections.map { section ->
        hashMapOf(
            "id" to section.id,
            "name" to section.name,
            "items" to section.items
        )
    }
    
    val data = hashMapOf(
        "name" to list.name,
        "items" to list.items,
        "sections" to sectionsData,
        "color" to list.color.value.toLong()
    )
    
    db.collection("users").document(userId)
        .collection("lists").document(list.id)
        .set(data)
}

fun deleteListFromFirestore(listId: String) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val db = FirebaseFirestore.getInstance()
    
    db.collection("users").document(userId)
        .collection("lists").document(listId)
        .delete()
}

suspend fun migrateLocalDataToFirestore(context: Context) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val db = FirebaseFirestore.getInstance()
    
    val existingLists = db.collection("users").document(userId)
        .collection("lists").get().await()
    
    if (!existingLists.isEmpty) return
    
    val prefsKeys = listOf("random_list", "dice_app")
    val listsToMigrate = mutableListOf<CustomList>()
    
    for (key in prefsKeys) {
        val prefs = context.getSharedPreferences(key, Context.MODE_PRIVATE)
        val jsonString = prefs.getString("custom_lists", null)
        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val itemsArray = jsonObject.getJSONArray("items")
                    val items = mutableListOf<String>()
                    for (j in 0 until itemsArray.length()) {
                        items.add(itemsArray.getString(j))
                    }
                    listsToMigrate.add(
                        CustomList(
                            id = jsonObject.optString("id", UUID.randomUUID().toString()),
                            name = jsonObject.getString("name"),
                            items = items,
                            color = Color(jsonObject.getLong("color").toULong())
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("Migration", "Error migrating data", e)
            }
        }
    }
    
    listsToMigrate.forEach { list ->
        saveListToFirestore(list)
    }
}

@Composable
fun LoginScreen(onSignInSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
            .background(backgroundGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "🎲",
                fontSize = 80.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.subtitle),
                fontSize = 16.sp,
                color = Color(0xFF8b8b8b),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(64.dp))
            
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        try {
                            val credentialManager = CredentialManager.create(context)
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId("446367114682-rc6b4rt69tsrj33276k0ic3n17aamp0m.apps.googleusercontent.com")
                                .build()
                            
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()
                            
                            val result = credentialManager.getCredential(context, request)
                            val credential = result.credential
                            
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            val googleIdToken = googleIdTokenCredential.idToken
                            
                            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                            FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
                            
                            migrateLocalDataToFirestore(context)
                            onSignInSuccess()
                        } catch (e: Exception) {
                            Log.e("LoginScreen", "Sign-in failed", e)
                            errorMessage = context.getString(R.string.sign_in_error)
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFe94560)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = stringResource(R.string.sign_in_google),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage!!,
                    color = Color(0xFFe94560),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun MainApp() {
    val auth = FirebaseAuth.getInstance()
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    if (currentUser == null) {
        LoginScreen(onSignInSuccess = {
            currentUser = auth.currentUser
        })
    } else {
        AuthenticatedApp(onSignOut = {
            auth.signOut()
            currentUser = null
        })
    }
}

@Composable
fun AuthenticatedApp(onSignOut: () -> Unit) {
    val context = LocalContext.current
    val customLists = remember { mutableStateListOf<CustomList>() }
    var listenerRegistration by remember { mutableStateOf<ListenerRegistration?>(null) }
    
    LaunchedEffect(Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        val db = FirebaseFirestore.getInstance()
        
        listenerRegistration = db.collection("users").document(userId)
            .collection("lists")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Firestore", "Listen failed", error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    customLists.clear()
                    for (doc in snapshot.documents) {
                        val data = doc.data ?: continue
                        try {
                            val sections = (data["sections"] as? List<*>)?.mapNotNull { raw ->
                                val map = raw as? Map<*, *> ?: return@mapNotNull null
                                ListSection(
                                    id = map["id"] as? String ?: UUID.randomUUID().toString(),
                                    name = map["name"] as? String ?: "",
                                    items = (map["items"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                                )
                            } ?: emptyList()
                            
                            customLists.add(
                                CustomList(
                                    id = doc.id,
                                    name = data["name"] as? String ?: "",
                                    items = (data["items"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                                    sections = sections,
                                    color = Color((data["color"] as? Long ?: 0L).toULong())
                                )
                            )
                        } catch (e: Exception) {
                            Log.e("Firestore", "Error parsing document", e)
                        }
                    }
                }
            }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            listenerRegistration?.remove()
        }
    }
    
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
                onListsChanged = {},
                onSignOut = onSignOut
            )
        }
    }
}

@Composable
fun ListsScreen(
    lists: MutableList<CustomList>,
    onListsChanged: () -> Unit,
    onSignOut: () -> Unit = {}
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var selectedList by remember { mutableStateOf<CustomList?>(null) }
    var selectedSectionedList by remember { mutableStateOf<CustomList?>(null) }
    var selectedSectionId by remember { mutableStateOf<String?>(null) }
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
            onCreate = { newList ->
                saveListToFirestore(newList)
                showCreateDialog = false
            }
        )
    }
    
    if (showSignOutDialog) {
        Dialog(onDismissRequest = { showSignOutDialog = false }) {
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
                            .background(Color(0xFF3a3a4a)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "👤", fontSize = 36.sp)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.sign_out_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.sign_out_message),
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
                            onClick = { showSignOutDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3a3a4a)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(52.dp)
                        ) {
                            Text(stringResource(R.string.button_cancel), color = Color.White, fontSize = 15.sp)
                        }
                        Button(
                            onClick = {
                                showSignOutDialog = false
                                onSignOut()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFe94560)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(52.dp)
                        ) {
                            Text(stringResource(R.string.sign_out), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
    
    if (selectedSectionedList != null && selectedList == null) {
        SectionsDialog(
            list = selectedSectionedList!!,
            onDismiss = { selectedSectionedList = null },
            onSelectSection = { section ->
                selectedSectionId = section.id
                selectedList = CustomList(
                    id = "${selectedSectionedList!!.id}_${section.id}",
                    name = section.name,
                    items = section.items,
                    color = selectedSectionedList!!.color
                )
            },
            onDelete = {
                deleteListFromFirestore(selectedSectionedList!!.id)
                selectedSectionedList = null
            },
            onEdit = { updatedList ->
                saveListToFirestore(updatedList)
                selectedSectionedList = updatedList
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
                selectedSectionId = null
                randomResult = null
            },
            onDelete = {
                if (selectedSectionId != null && selectedSectionedList != null) {
                    val updatedSections = selectedSectionedList!!.sections.filter { it.id != selectedSectionId }
                    val updatedParent = selectedSectionedList!!.copy(sections = updatedSections)
                    saveListToFirestore(updatedParent)
                    selectedSectionedList = updatedParent
                } else {
                    deleteListFromFirestore(selectedList!!.id)
                }
                selectedList = null
                selectedSectionId = null
                randomResult = null
            },
            onEdit = { updatedList ->
                if (selectedSectionId != null && selectedSectionedList != null) {
                    val updatedSections = selectedSectionedList!!.sections.map { section ->
                        if (section.id == selectedSectionId) {
                            section.copy(name = updatedList.name, items = updatedList.items)
                        } else section
                    }
                    val updatedParent = selectedSectionedList!!.copy(sections = updatedSections)
                    saveListToFirestore(updatedParent)
                    selectedSectionedList = updatedParent
                } else {
                    saveListToFirestore(updatedList)
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
            Column(modifier = Modifier.weight(1f)) {
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
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF3a3a4a))
                        .clickable { showSignOutDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "👤",
                        fontSize = 24.sp
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
                        onClick = {
                            if (list.hasSections) {
                                selectedSectionedList = list
                            } else {
                                selectedList = list
                            }
                        }
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
                            text = if (list.hasSections)
                                stringResource(R.string.sections_count, list.sections.size)
                            else
                                stringResource(R.string.items_count, list.items.size),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = list.color
                        )
                    }
                }
                
                if (list.hasSections) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        list.sections.take(3).forEach { section ->
                            Text(
                                text = "• ${section.name}",
                                fontSize = 13.sp,
                                color = Color(0xFF9b9b9b),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (list.sections.size > 3) {
                            Text(
                                text = stringResource(R.string.more_items, list.sections.size - 3),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = list.color.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else if (list.items.isNotEmpty()) {
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
                    text = if (list.hasSections) "📂" else "🎲",
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
    onCreate: (CustomList) -> Unit
) {
    var listName by remember { mutableStateOf("") }
    var currentItem by remember { mutableStateOf("") }
    var isSectioned by remember { mutableStateOf(false) }
    val items = remember { mutableStateListOf<String>() }
    val sections = remember { mutableStateListOf<ListSection>() }
    var currentSectionName by remember { mutableStateOf("") }
    var editingSectionIndex by remember { mutableStateOf(-1) }
    val sectionItems = remember { mutableStateListOf<String>() }
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
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(false, true).forEach { sectioned ->
                        val isSelected = isSectioned == sectioned
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFFe94560).copy(alpha = 0.2f) else Color(0xFF1a1a2e))
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) Color(0xFFe94560) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    isSectioned = sectioned
                                    items.clear()
                                    sections.clear()
                                    sectionItems.clear()
                                    currentSectionName = ""
                                    editingSectionIndex = -1
                                }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (sectioned) stringResource(R.string.list_type_sections) else stringResource(R.string.list_type_simple),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFFe94560) else Color(0xFF6b6b7b),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                if (!isSectioned) {
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
                
                } else {
                    Text(
                        text = stringResource(R.string.label_elements).uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6b6b7b),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (editingSectionIndex >= 0) {
                        Text(
                            text = "📝 ${sections.getOrNull(editingSectionIndex)?.name ?: ""}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFe94560)
                        )
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
                                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
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
                                                sectionItems.add(currentItem.trim())
                                                currentItem = ""
                                            }
                                        }
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (currentItem.isNotBlank()) Brush.linearGradient(listOf(Color(0xFF00b894), Color(0xFF00d9a5)))
                                        else Brush.linearGradient(listOf(Color(0xFF3a3a4a), Color(0xFF3a3a4a)))
                                    )
                                    .clickable {
                                        if (currentItem.isNotBlank()) {
                                            sectionItems.add(currentItem.trim())
                                            currentItem = ""
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "+", fontSize = 26.sp, fontWeight = FontWeight.Light, color = Color.White)
                            }
                        }
                        
                        if (sectionItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyColumn(modifier = Modifier.heightIn(max = 100.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                itemsIndexed(sectionItems.toList()) { idx, item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1a1a2e)).padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "• $item", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                        Box(
                                            modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(0xFFe94560).copy(alpha = 0.15f)).clickable { sectionItems.removeAt(idx) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "×", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFe94560))
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF3a3a4a))
                                    .clickable {
                                        sectionItems.clear()
                                        currentItem = ""
                                        editingSectionIndex = -1
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(text = stringResource(R.string.button_cancel), fontSize = 13.sp, color = Color.White)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFF00b894), Color(0xFF00d9a5))))
                                    .clickable {
                                        if (editingSectionIndex >= 0 && editingSectionIndex < sections.size) {
                                            sections[editingSectionIndex] = sections[editingSectionIndex].copy(items = sectionItems.toList())
                                        }
                                        sectionItems.clear()
                                        currentItem = ""
                                        editingSectionIndex = -1
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(text = stringResource(R.string.button_save), fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
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
                                if (currentSectionName.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.section_name_placeholder),
                                        fontSize = 16.sp,
                                        color = Color(0xFF5a5a6a)
                                    )
                                }
                                BasicTextField(
                                    value = currentSectionName,
                                    onValueChange = { currentSectionName = it },
                                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                                    cursorBrush = SolidColor(Color(0xFFe94560)),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Sentences,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (currentSectionName.isNotBlank()) {
                                                sections.add(ListSection(name = currentSectionName.trim(), items = emptyList()))
                                                currentSectionName = ""
                                            }
                                        }
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (currentSectionName.isNotBlank()) Brush.linearGradient(listOf(Color(0xFF00b894), Color(0xFF00d9a5)))
                                        else Brush.linearGradient(listOf(Color(0xFF3a3a4a), Color(0xFF3a3a4a)))
                                    )
                                    .clickable {
                                        if (currentSectionName.isNotBlank()) {
                                            sections.add(ListSection(name = currentSectionName.trim(), items = emptyList()))
                                            currentSectionName = ""
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "+", fontSize = 26.sp, fontWeight = FontWeight.Light, color = Color.White)
                            }
                        }
                        
                        if (sections.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(sections.size) { idx ->
                                    val section = sections[idx]
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF1a1a2e))
                                            .clickable {
                                                sectionItems.clear()
                                                sectionItems.addAll(section.items)
                                                editingSectionIndex = idx
                                            }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = section.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                text = stringResource(R.string.section_elements, section.items.size),
                                                fontSize = 12.sp,
                                                color = Color(0xFF6b6b7b)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFe94560).copy(alpha = 0.15f))
                                                .clickable {
                                                    val removeIdx = idx
                                                    sections.removeAt(removeIdx)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "×", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFe94560))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(28.dp))
                
                val canCreate = listName.isNotBlank() && (
                    (!isSectioned && items.isNotEmpty()) ||
                    (isSectioned && sections.isNotEmpty())
                )
                
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
                            if (isSectioned && editingSectionIndex >= 0 && editingSectionIndex < sections.size) {
                                sections[editingSectionIndex] = sections[editingSectionIndex].copy(items = sectionItems.toList())
                                sectionItems.clear()
                                currentItem = ""
                                editingSectionIndex = -1
                            }
                            
                            val finalCanCreate = listName.isNotBlank() && (
                                (!isSectioned && items.isNotEmpty()) ||
                                (isSectioned && sections.isNotEmpty())
                            )
                            
                            if (finalCanCreate) {
                                if (isSectioned) {
                                    onCreate(CustomList(name = listName.trim(), sections = sections.toList()))
                                } else {
                                    onCreate(CustomList(name = listName.trim(), items = items.toList()))
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canCreate) Color(0xFFe94560) else Color(0xFF3a3a4a)
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
fun SectionsDialog(
    list: CustomList,
    onDismiss: () -> Unit,
    onSelectSection: (ListSection) -> Unit,
    onDelete: () -> Unit,
    onEdit: (CustomList) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember(list.id, list.name) { mutableStateOf(list.name) }
    var newSectionName by remember { mutableStateOf("") }
    val editSections = remember(list.id, list.sections) { mutableStateListOf<ListSection>().also { it.addAll(list.sections) } }
    
    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            listName = list.name,
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirm = false }
        )
    }
    
    Dialog(
        onDismissRequest = {
            if (isEditing && editName.isNotBlank()) {
                onEdit(list.copy(name = editName.trim(), sections = editSections.toList()))
            }
            onDismiss()
        },
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
                                            onEdit(list.copy(name = editName.trim(), sections = editSections.toList()))
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
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF3a3a4a))
                                .clickable {
                                    if (isEditing && editName.isNotBlank()) {
                                        onEdit(list.copy(name = editName.trim(), sections = editSections.toList()))
                                    }
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8b8b8b)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.sections_count, if (isEditing) editSections.size else list.sections.size),
                    fontSize = 13.sp,
                    color = Color(0xFF6b6b7b)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
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
                            if (newSectionName.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.section_name_placeholder),
                                    fontSize = 15.sp,
                                    color = Color(0xFF5a5a6a)
                                )
                            }
                            BasicTextField(
                                value = newSectionName,
                                onValueChange = { newSectionName = it },
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
                                        if (newSectionName.isNotBlank()) {
                                            editSections.add(ListSection(name = newSectionName.trim(), items = emptyList()))
                                            newSectionName = ""
                                        }
                                    }
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        
                        val addBg = if (newSectionName.isNotBlank())
                            Brush.linearGradient(listOf(Color(0xFF00b894), Color(0xFF00d9a5)))
                        else Brush.linearGradient(listOf(Color(0xFF3a3a4a), Color(0xFF3a3a4a)))
                        
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(addBg)
                                .clickable {
                                    if (newSectionName.isNotBlank()) {
                                        editSections.add(ListSection(name = newSectionName.trim(), items = emptyList()))
                                        newSectionName = ""
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "+", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Light)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                LazyColumn(
                    modifier = Modifier.heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val displaySections = if (isEditing) editSections.toList() else list.sections
                    itemsIndexed(displaySections) { index, section ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1a1a2e))
                                .then(
                                    if (!isEditing) Modifier.clickable { onSelectSection(section) }
                                    else Modifier
                                )
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = section.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.section_elements, section.items.size),
                                        fontSize = 12.sp,
                                        color = list.color
                                    )
                                }
                                if (isEditing) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFe94560).copy(alpha = 0.15f))
                                            .clickable { editSections.removeAt(index) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "×",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFe94560)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(list.color.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "🎲", fontSize = 20.sp)
                                    }
                                }
                            }
                        }
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
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF3a3a4a))
                                .clickable { saveChangesAndClose() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8b8b8b)
                            )
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
