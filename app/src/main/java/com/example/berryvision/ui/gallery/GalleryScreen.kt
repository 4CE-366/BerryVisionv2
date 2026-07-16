package com.example.berryvision.ui.gallery

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

data class GalleryItem(val file: File, val count: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    onOpenCamera: () -> Unit
) {
    val context = LocalContext.current
    var images by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        val directory = context.getDir("berryvision_photos", Context.MODE_PRIVATE)
        if (directory.exists()) {
            val files = directory.listFiles()?.filter { it.extension == "jpg" }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
            
            images = files.map { file ->
                val countRegex = Regex("_C(\\d+)\\.jpg")
                val match = countRegex.find(file.name)
                val count = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
                GalleryItem(file, count)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Galería de Cosecha") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenCamera,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Tomar Foto")
            }
        }
    ) { paddingValues ->
        if (images.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No hay fotos guardadas aún.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(images) { item ->
                    GalleryImageCard(item)
                }
            }
        }
    }
}

@Composable
fun GalleryImageCard(item: GalleryItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.file,
                contentDescription = "Fresa detectada",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "🍓 ${item.count}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
