package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.ui.i18n.StringsProvider

@Composable
fun AuthScreen(
  languageCode: String,
  onLanguageChange: (String) -> Unit,
  onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val strings = StringsProvider.forLang(languageCode)

  var isCheckingAutoSignIn by remember { mutableStateOf(true) }
  var isSigningIn by remember { mutableStateOf(false) }
  var authError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = {
        isCheckingAutoSignIn = false
        onAuthSuccess()
      },
      onUnauthenticated = { isCheckingAutoSignIn = false },
      scope = scope,
    )
  }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(
          Brush.verticalGradient(
            listOf(
              MaterialTheme.colorScheme.background,
              MaterialTheme.colorScheme.surface,
              MaterialTheme.colorScheme.background,
            )
          )
        )
        .statusBarsPadding()
        .navigationBarsPadding(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier.widthIn(max = 520.dp)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(
          onClick = { onLanguageChange(if (languageCode == "ru") "en" else "ru") },
          modifier = Modifier.testTag("auth_language_toggle"),
        ) {
          Text(
            text = if (languageCode == "ru") "EN" else "RU",
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }

      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(24.dp)),
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_rustgram_hero_1794847928168),
          contentDescription = "Rustgram architecture hero illustration",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(
                Brush.verticalGradient(
                  listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                )
              )
        )
        Row(
          modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier.size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.RocketLaunch,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimary,
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "RUSTGRAM",
              style = MaterialTheme.typography.titleLarge,
              color = Color.White,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = strings.tagline,
              style = MaterialTheme.typography.bodyMedium,
              color = Color.White.copy(alpha = 0.85f),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = strings.signInSubtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(20.dp))

      Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          FeatureBullet(
            icon = Icons.Filled.Lock,
            title = if (languageCode == "ru") "Защищённый облачный профиль" else "Secured Cloud Sync",
            subtitle =
              if (languageCode == "ru") "Чаты, каналы, черновики и правила доступа Firestore"
              else "Chats, channels, drafts & zero-trust Firestore access rules",
          )
          FeatureBullet(
            icon = Icons.Filled.AutoAwesome,
            title = if (languageCode == "ru") "Встроенные Gemini AI-боты" else "Multi-Turn Gemini AI Bots",
            subtitle =
              if (languageCode == "ru") "Потоковые ответы, системные роли и выбор моделей"
              else "Streaming answers, custom system roles & model tier selection",
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      AnimatedVisibility(visible = authError != null) {
        Surface(
          color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
          Text(
            text = authError.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
          )
        }
      }

      Button(
        onClick = {
          authError = null
          isSigningIn = true
          onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
              isSigningIn = false
              onAuthSuccess()
            },
            onAuthError = { msg ->
              isSigningIn = false
              authError = msg
            },
            scope = scope,
            onAuthCancelled = { isSigningIn = false },
          )
        },
        enabled = !isSigningIn && !isCheckingAutoSignIn,
        shape = RoundedCornerShape(16.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
        modifier =
          Modifier.fillMaxWidth()
            .height(54.dp)
            .testTag("google_sign_in_button"),
      ) {
        if (isSigningIn || isCheckingAutoSignIn) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
          Spacer(modifier = Modifier.width(12.dp))
          Text(
            text = if (isCheckingAutoSignIn) strings.checkingSession else strings.signInWithGoogle,
            style = MaterialTheme.typography.titleMedium,
          )
        } else {
          Text(
            text = strings.signInWithGoogle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}

@Composable
private fun FeatureBullet(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier =
        Modifier.size(36.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
      )
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column {
      Text(text = title, style = MaterialTheme.typography.titleMedium)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
