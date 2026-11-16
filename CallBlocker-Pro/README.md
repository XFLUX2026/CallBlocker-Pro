# CallBlocker Pro

Aplicativo Android nativo em Kotlin e Jetpack Compose para filtragem de chamadas recebidas por lista de permissões.

- Contatos da agenda são permitidos quando o usuário concede READ_CONTACTS.
- Números adicionados manualmente são permitidos.
- Números fora da lista, inclusive ocultos/privados, são rejeitados.
- O histórico é armazenado localmente com Room.
- Não mantém serviço permanente nem notificação persistente.
- Android 10 (API 29) ou superior; SDK 35 e JDK 17 para compilar.
- O usuário precisa definir o app como filtro de chamadas nas configurações de função do Android.

O histórico legado é importado uma única vez na primeira abertura da versão com Room. O comportamento precisa ser validado em aparelho real.
