# Bloqueia Ads

Aplicativo Android nativo em Kotlin para filtragem DNS local, sem servidor VPN externo.

## Estado

Versão inicial de desenvolvimento. Ainda requer compilação e testes em dispositivo real; não prometa bloqueio total de anúncios. Anúncios do Facebook podem ser servidos pela mesma infraestrutura do conteúdo e continuar aparecendo.

## Compilar

O projeto usa Gradle e Android SDK. Execute `./gradlew testDebugUnitTest assembleDebug` em ambiente com Java 17 e Android SDK configurados.

O APK de depuração, se a compilação passar, será gerado em `app/build/outputs/apk/debug/app-debug.apk`.

## Privacidade e limitações

- A filtragem ocorre localmente por `VpnService`.
- O MVP filtra apenas DNS IPv4/UDP e não é uma VPN de tráfego completo.
- DNS-over-HTTPS/TLS, IPv6, DNS por TCP e anúncios servidos pelo mesmo domínio do conteúdo podem escapar.
- Uma consulta permitida é encaminhada ao resolvedor DNS configurado no código. Não há túnel VPN remoto.
- Teste conectividade, bateria e o Facebook oficial antes de uso contínuo.
