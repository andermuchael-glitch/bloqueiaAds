package br.com.bloqueiaads

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import br.com.bloqueiaads.core.LocalDnsVpnService

private val Navy = Color(0xFF0B1220)
private val Panel = Color(0xFF131F32)
private val Green = Color(0xFF2DD4A7)
private val Muted = Color(0xFF9AAAC0)

class MainActivity : ComponentActivity() {
    private var isActive by mutableStateOf(false)
    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) startProtection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Green, background = Navy, surface = Panel)) {
                Dashboard(isActive = isActive, onToggle = { enabled ->
                    if (enabled) requestProtection() else stopProtection()
                })
            }
        }
    }

    private fun requestProtection() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) vpnPermission.launch(prepare) else startProtection()
    }

    private fun startProtection() {
        val intent = Intent(this, LocalDnsVpnService::class.java)
        ContextCompat.startForegroundService(this, intent)
        isActive = true
    }

    private fun stopProtection() {
        stopService(Intent(this, LocalDnsVpnService::class.java))
        isActive = false
    }
}

@Composable
private fun Dashboard(isActive: Boolean, onToggle: (Boolean) -> Unit) {
    var showLimits by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Navy, Color(0xFF101D30))))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).background(Green.copy(alpha = .15f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Green, modifier = Modifier.size(29.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Bloqueia Ads", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Privacidade sob seu controle", color = Muted, fontSize = 13.sp)
            }
        }
        Column(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF163B3A), Panel)), RoundedCornerShape(26.dp)
            ).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (isActive) Icons.Default.CheckCircle else Icons.Default.Security, contentDescription = null, tint = Green, modifier = Modifier.size(20.dp))
                Text(if (isActive) "PROTEÇÃO ATIVA" else "PROTEÇÃO DESATIVADA", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            }
            Text(if (isActive) "Seu filtro DNS está em execução" else "Bloqueie domínios conhecidos de anúncios", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Text("Filtragem local • Sem servidor VPN externo", color = Muted, fontSize = 13.sp)
            Button(
                onClick = { onToggle(!isActive) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isActive) Color(0xFF334155) else Green, contentColor = if (isActive) Color.White else Navy)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(Modifier.width(9.dp))
                Text(if (isActive) "Pausar proteção" else "Ativar proteção", fontWeight = FontWeight.Bold)
            }
        }
        Text("RESUMO DA PROTEÇÃO", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Domínios bloqueados", "—", "Contagem real em próxima etapa", Modifier.weight(1f))
            StatCard("Consultas filtradas", "—", "Estatísticas em evolução", Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Security, contentDescription = null, tint = Green)
                Text("Compatibilidade primeiro", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Text("O filtro inicial é conservador. Alguns anúncios podem continuar aparecendo, especialmente no Facebook oficial.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            TextButton(onClick = { showLimits = !showLimits }, contentPadding = PaddingValues(0.dp)) {
                Text(if (showLimits) "Ocultar limitações" else "Ver limitações técnicas")
            }
            if (showLimits) Text(
                "Esta versão filtra DNS IPv4 por UDP. DNS por HTTPS/TLS, IPv6, DNS por TCP e anúncios servidos pelo mesmo domínio do conteúdo podem escapar. Em falhas de rede, consultas podem expirar. Não é uma VPN de tráfego completo.",
                color = Color(0xFFFFD18B), fontSize = 12.sp, lineHeight = 18.sp
            )
        }
        Spacer(Modifier.weight(1f))
        Text("BLOQUEIA ADS • VERSÃO DE TESTE 0.1.0", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun StatCard(title: String, value: String, foot: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Panel, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Muted, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(foot, color = Green, fontSize = 10.sp)
    }
}
