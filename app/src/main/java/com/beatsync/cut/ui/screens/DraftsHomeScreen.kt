package com.beatsync.cut.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatsync.cut.model.ProjectDraft
import com.beatsync.cut.ui.components.formatMs
import com.beatsync.cut.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DraftsHomeScreen(
    drafts: List<ProjectDraft>,
    onNewManualProject: () -> Unit,
    onNewAutoBeatSyncProject: () -> Unit,
    onOpenDraft: (ProjectDraft) -> Unit,
    onDuplicateDraft: (ProjectDraft) -> Unit,
    onDeleteDraft: (String) -> Unit,
    onRelinkMedia: (String, String) -> Unit
) {
    val dateFmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = PantoneBlueLight, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
            Text("BeatSync Cut", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(PantoneBlue, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("v2.0", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Dual Entry Cards: Manual Studio vs 2-Step Auto Beat-Sync
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onNewManualProject,
                colors = ButtonDefaults.buttonColors(containerColor = PantoneBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(54.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Proyek Baru", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = onNewAutoBeatSyncProject,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(54.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PantoneBlueLight)
                Spacer(Modifier.width(6.dp))
                Text("Auto Beat-Sync", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "PROYEK SAYA (${drafts.size} Draft Tersimpan)",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )

        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(drafts, key = { it.id }) { draft ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDark)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                        .clickable { onOpenDraft(draft) }
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(draft.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${draft.slots.size} Klip • ${formatMs(draft.totalTimelineDurationMs)} • ${draft.aspectRatio.label} • ${dateFmt.format(Date(draft.updatedAtMs))}",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        Row {
                            IconButton(onClick = { onDuplicateDraft(draft) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Duplikat", tint = PantoneBlueLight, modifier = Modifier.size(17.dp))
                            }
                            IconButton(onClick = { onDeleteDraft(draft.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = DangerRed, modifier = Modifier.size(17.dp))
                            }
                        }
                    }

                    // Relink Media Warning if a video file was moved/deleted
                    val missingItem = draft.mediaBin.firstOrNull { it.isMissing }
                    if (missingItem != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DangerRed.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Media hilang: ${missingItem.displayName}",
                                color = DangerRed,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { onRelinkMedia(draft.id, missingItem.id) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Relink", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
