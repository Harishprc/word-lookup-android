package com.harish.wordlookup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.harish.wordlookup.data.Languages
import com.harish.wordlookup.ui.components.BrandMark
import com.harish.wordlookup.ui.components.OnboardingProgress
import com.harish.wordlookup.ui.components.SectionCard
import com.harish.wordlookup.ui.components.filledFieldColors
import com.harish.wordlookup.ui.theme.Radius
import com.harish.wordlookup.ui.theme.Spacing

/**
 * Doubles as onboarding (first run, hasGeminiKey false) and as the "Change
 * language / API key" edit screen reached from Home - same copy either way,
 * only the field placeholder differs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    currentLanguage: String,
    hasGeminiKey: Boolean,
    onSave: (language: String, geminiKey: String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var language by rememberSaveable { mutableStateOf(currentLanguage) }
    var geminiKey by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.ProminentCard),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = Spacing.ProminentCard),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(glyph = Languages.get(language).glyph, size = 40.dp)
            OnboardingProgress(step = 1, totalSteps = 2)
        }

        Text("Make every word clear.", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Select text in any app and see a dictionary card: meaning, synonyms, an example, and the translation you pick below.",
            modifier = Modifier.padding(top = Spacing.SM, bottom = Spacing.SM),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionCard(title = "Translate into") {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                TextField(
                    value = language,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Language") },
                    // menuAnchor() is what wires the field to the dropdown. Without
                    // it Material3 has nothing to anchor or open against, so tapping
                    // the field did nothing at all - the "can't change the language"
                    // bug. PrimaryNotEditable is the read-only-field variant.
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(Radius.LG),
                    colors = filledFieldColors(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    Languages.ALL.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text("${lang.glyph}  ${lang.name}") },
                            onClick = { language = lang.name; expanded = false },
                        )
                    }
                }
            }
        }

        SectionCard(title = "Connect Gemini") {
            TextField(
                value = geminiKey,
                onValueChange = { geminiKey = it },
                label = { Text("API key") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(Radius.LG),
                colors = filledFieldColors(),
                placeholder = { Text(if (hasGeminiKey) "Leave blank to keep the current key" else "Paste your key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            Text(
                buildAnnotatedString {
                    append("Free, no credit card. ")
                    withLink(
                        LinkAnnotation.Url(
                            url = "https://aistudio.google.com/apikey",
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                        ),
                    ) {
                        append("aistudio.google.com")
                    }
                    append(" → \"Get API key\". Roughly 1,500 lookups/day on the free tier.")
                },
                modifier = Modifier.padding(top = Spacing.SM),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Surface(
            shape = RoundedCornerShape(Radius.LG),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.SM, bottom = Spacing.ProminentCard),
        ) {
            Row(Modifier.padding(Spacing.MD), verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(Spacing.SM + Spacing.XS))
                Column {
                    Text("Encrypted on this phone", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "The key never leaves the device except to reach Google's API. You can change it later from the main screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Button(
            onClick = { onSave(language, geminiKey) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(Radius.LG),
        ) {
            Text("Continue")
        }
    }
}
