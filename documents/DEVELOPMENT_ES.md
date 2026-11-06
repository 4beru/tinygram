# Desarrollo local y personalización por USB

Guía para Linux desde la raíz del repositorio. El flujo usa el teléfono físico y la variante `debug`, sin exigir Android Studio ni emulador. Al final se distingue lo verificado de las comprobaciones pendientes en el teléfono.

## 1. Entrar en el entorno reproducible

Requisitos del anfitrión: Nix y Devenv disponibles. La configuración se comprobó con Nix 2.30.2 y Devenv 2.2.2. `devenv.nix`, `devenv.yaml` y `devenv.lock` definen el entorno y fijan sus entradas. Conserva el lock para reproducirlo, no lo actualices como paso de preparación.

```bash
git submodule update --init --recursive
devenv shell
```

Mantén esta shell abierta para el resto de los comandos. La primera entrada puede descargar herramientas y necesita red y espacio en disco. No uses `git submodule update --remote`: se necesitan los commits fijados por el proyecto.

El entorno proporciona JDK 17 para el lanzador y las herramientas Android, además de JDK 25 para cumplir `gradle/gradle-daemon-jvm.properties`. La compatibilidad del código Android sigue siendo Java 17: no se modifican esos requisitos. También proporciona Android SDK Platform 37.0, Build Tools 36.0.0, platform-tools, NDK 28.2.13676358, Rust 1.98.0 y `cargo-ndk`. Incluye los destinos `aarch64-linux-android`, `armv7-linux-androideabi` y `x86_64-linux-android`. Usa siempre `./gradlew`, sin instalar Gradle o Rust mediante gestores globales ni ejecutar `rustup target add` fuera del entorno.

`ANDROID_HOME`, `ANDROID_SDK_ROOT` y las variables del NDK apuntan al SDK de Nix. `CARGO_HOME` se aísla automáticamente en `.devenv/state/cargo` para evitar proxies de Rust del anfitrión. `GRADLE_USER_HOME` se aísla en `.devenv/state/gradle`, con propiedades generadas que seleccionan los JDK de Nix y el `aapt2` compatible con Nix. La detección de JDK globales y su descarga automática están desactivadas. No sustituyas estas rutas por las globales ni edites el archivo generado `gradle.properties` del estado local.

```bash
java -version
"$JDK25_HOME/bin/java" -version
rustc --version
cargo ndk --version
adb version
```

Opcional: si ya usas direnv con soporte Devenv, revisa `.envrc` y ejecuta `direnv allow` para cargar automáticamente el entorno. No es necesario para `devenv shell`.

## 2. Configurar SDK y credenciales sin exponer secretos

Necesitas credenciales propias de Telegram en el almacén `pass`, bajo `tinygram/api_id` y `tinygram/api_hash`. Cada entrada debe contener únicamente el valor requerido. Si faltan, créalas de forma interactiva, sin escribir el secreto en la línea de comandos:

```bash
pass insert tinygram/api_id
pass insert tinygram/api_hash
```

No sobrescribas entradas existentes sin revisarlas de manera privada. Desbloquea GPG normalmente mediante tu agente y pinentry cuando se solicite. No muestres `pass show`, no uses trazas de shell para investigar secretos y no copies sus valores a registros, Nix o mensajes.

```bash
tinygram-configure
```

El helper escribe únicamente `local.properties` en la raíz, ignorado por Git y con permisos privados. Actualiza `sdk.dir`, conserva credenciales válidas existentes y ajustes ajenos, y consulta `pass` solo para credenciales ausentes. Un `API_ID` debe ser un entero positivo de 32 bits y `API_HASH`, 32 caracteres hexadecimales. Valores de ejemplo, claves gestionadas duplicadas o formatos ambiguos se rechazan sin modificar el archivo.

En esta preparación se tomaron únicamente `API_ID` y `API_HASH` de la configuración privada del proyecto hermano, porque GPG no permitió descifrar `pass` sin interacción. Ya están en `local.properties` con permisos `600`. `tinygram-configure` las conserva, por lo que no necesita volver a descifrarlas mientras sean válidas. No se copiaron configuraciones de producción, Firebase ni claves de firma.

Si ya copiaste `local.properties.example`, corrige o elimina **solo** las entradas inválidas `API_ID`/`API_HASH` en un editor privado antes de reintentar. No borres el archivo completo ni otros ajustes. Si falla GPG, resuelve el desbloqueo en tu sesión y vuelve a ejecutar el helper. Nunca confirmes `local.properties` ni credenciales en Git.

## 3. Elegir explícitamente el teléfono

Activa las opciones de desarrollador y la depuración USB, conecta un cable de datos y acepta la autorización RSA en el teléfono desbloqueado.

```bash
adb devices -l
export ANDROID_SERIAL='SERIAL_ELEGIDO_DE_LA_LISTA'
adb get-state
```

Sustituye el marcador por el identificador de tu teléfono que aparezca con estado `device`. No copies un identificador de otra persona. Mantén `ANDROID_SERIAL` seleccionado incluso si solo hay un dispositivo, y repite la selección al cambiar de shell o teléfono. Gradle y ADB heredarán esa selección.

Comprueba versión y arquitecturas antes de compilar:

```bash
API=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
ABIS=$(adb shell getprop ro.product.cpu.abilist | tr -d '\r')
printf 'API=%s\nABIs=%s\n' "$API" "$ABIS"
```

Se requiere API 24 o superior y al menos una ABI compatible: `arm64-v8a`, `armeabi-v7a` o `x86_64`. El teléfono observado durante la preparación tenía Android 10/API 29 y `arm64-v8a`, con ADB autorizado. Verifica tu dispositivo actual y elige una ABI de su lista. Para ese teléfono:

```bash
export ABI=arm64-v8a
```

No continúes si la API es inferior a 24 o la ABI elegida no aparece en `ABIS`.

## 4. Primera compilación e instalación

```bash
./gradlew :app:installDebug -PtargetAbi="$ABI" &&
  timeout 20s adb shell am start -n org.monogram.debug/org.monogram.MainActivity
```

La primera compilación **no** debe llevar `-PskipNativeBuild=true`: debe compilar Rust y generar los bindings UniFFI. Elegir una sola ABI reduce el trabajo respecto a compilar las tres. Este flujo instala `org.monogram.debug`, no la aplicación de producción `org.monogram`, y no requiere firma de producción. Comprueba visualmente en el teléfono que se abrió la variante esperada.

Conserva la clave debug habitual de Android (`~/.android/debug.keystore`) de forma privada: cambiarla puede impedir actualizar una instalación existente con la misma identidad. No la añadas a Git. La primera compilación nativa puede tardar bastante y usar varios GiB de RAM. Las siguientes reutilizan las cachés.

El proyecto guarda algunas bibliotecas nativas en Git. Una compilación puede mostrar `native/markup/src/main/jniLibs/arm64-v8a/libmonogram_markup.so` como modificado aunque no hayas cambiado Rust. Es un resultado generado, no una personalización de interfaz. Revisa `git status` y no lo incluyas accidentalmente en un commit de configuración o UI.

## 5. Cambiar la interfaz y volver a verla

Busca componentes compartidos en `core/ui` y la pantalla afectada en `feature/*` antes de editar. Reutiliza el tema y los recursos existentes. Los textos visibles deben ir a recursos y traducirse en todos los idiomas ya presentes en ese módulo. No edites bindings UniFFI generados a mano.

Después de guardar los cambios, el flujo general es:

```bash
./gradlew :app:installDebug -PtargetAbi="$ABI" &&
  timeout 10s adb shell am force-stop org.monogram.debug &&
  timeout 20s adb shell am start -n org.monogram.debug/org.monogram.MainActivity
```

La detención y reapertura hacen visible el código instalado. Los comandos ADB tienen límites de tiempo para no quedar esperando indefinidamente a un dispositivo que no responde. Un código `124` indica que expiró ese límite, no confirma por sí solo un fallo de la app. No borran los datos persistidos, aunque se pierde el estado transitorio del proceso. Gradle reutiliza trabajo incremental: esto es recompilar, reinstalar y reiniciar, **no hot reload**.

Solo después de una compilación nativa y generación de bindings satisfactorias para la **misma ABI**, y únicamente para cambios exclusivos de Kotlin, puedes usar:

```bash
./gradlew :app:installDebug -PtargetAbi="$ABI" -PskipNativeBuild=true &&
  timeout 10s adb shell am force-stop org.monogram.debug &&
  timeout 20s adb shell am start -n org.monogram.debug/org.monogram.MainActivity
```

No uses esa opción para cambios en Rust, contratos UniFFI, submódulos o configuración nativa, ni al cambiar de ABI. Omite tanto la compilación nativa como la regeneración de bindings y puede dejar artefactos incompatibles. Ante dudas, vuelve al comando general sin la opción.

### Reinstalar automáticamente al guardar cambios de interfaz

Después de completar la primera compilación, puedes dejar este comando opcional abierto en la raíz, con `ANDROID_SERIAL` y `ABI` exportados. `watchexec` está incluido y fijado por Nix. Ejecuta una instalación inicial y después agrupa los cambios de archivos Kotlin/XML, sin iniciar compilaciones superpuestas:

```bash
watchexec --shell=none --debounce 2s --on-busy-update queue \
  --watch app/src --watch core --watch feature --watch network \
  --exts kt,xml --ignore '**/build/**' -- \
  bash -c '
    set -euo pipefail
    : "${ANDROID_SERIAL:?Selecciona el teléfono}" "${ABI:?Selecciona la ABI}"
    ./gradlew :app:installDebug -PtargetAbi="$ABI"
    timeout 10s adb shell am force-stop org.monogram.debug
    timeout 20s adb shell am start -n org.monogram.debug/org.monogram.MainActivity
  '
```

El watcher usa la compilación normal incremental, sin omitir tareas nativas: Gradle decide qué puede reutilizar. Solo observa Kotlin/XML en las rutas indicadas, no Rust ni cambios del entorno o dependencias. Detén el watcher con `Ctrl+C` antes de esos cambios y ejecuta manualmente la compilación completa. Cada instalación correcta reinicia el proceso y pierde su estado transitorio. Es actualización automática tras compilar, no una vista instantánea ni Live Edit. No ejecutes otra compilación manual mientras esté activo.

### Android Studio es opcional

Si ya lo tienes, ábrelo desde esta shell mediante su lanzador para que herede el entorno, no desde una instancia previamente abierta sin esas variables. Configura Gradle JVM con el JDK 25 de `JDK25_HOME`, el SDK con `ANDROID_HOME`, el wrapper del proyecto, Gradle User Home con `GRADLE_USER_HOME` y la variante `debug`. Así el IDE también utiliza las propiedades generadas que fijan Java y `aapt2`. No intentes instalar paquetes dentro del SDK inmutable de Nix. Selecciona también el teléfono en el IDE.

El catálogo del proyecto usa AGP 9.3.3. La tabla oficial consultada el 5 de noviembre de 2026 indica soporte de la familia AGP 9.3 en Android Studio Quail 3 (2026.1.3). Consulta la compatibilidad oficial de tu versión antes de sincronizar, sin aceptar actualizaciones de AGP como solución automática.

Live Edit de Compose requiere Android 11/API 30 o superior, por lo que **no está disponible en el teléfono Android 10/API 29 observado**. Apply Changes es otra función, limitada a cambios compatibles y APK de depuración en API 26 o superior. Puede requerir reiniciar actividad o volver a ejecutar la aplicación, y no sustituye la recompilación nativa. El flujo CLI anterior sigue siendo la alternativa explícita.

Fuentes oficiales consultadas para compatibilidad y despliegue:

- <https://developer.android.com/build/releases/about-agp>
- <https://developer.android.com/develop/ui/compose/tooling/iterative-development>
- <https://developer.android.com/studio/run#apply-changes>

## 6. Diagnóstico seguro

- **`unauthorized`**: desbloquea el teléfono, acepta el diálogo RSA y vuelve a conectar si hace falta. No continúes hasta ver `device`.
- **Lista vacía**: comprueba cable de datos, puerto y depuración USB. **Más de un dispositivo**: vuelve a seleccionar `ANDROID_SERIAL` explícitamente.
- **`no permissions`**: revisa las reglas udev Android existentes del anfitrión. En este Arch ya existe `/usr/lib/udev/rules.d/51-android.rules`, con reglas `uaccess`. Comprueba que tu sesión local recibe acceso y reconecta el USB tras cualquier corrección administrativa. No ejecutes ADB como root ni cambies grupos automáticamente.
- **Fallo de SDK, Java o Cargo**: confirma que estás dentro de `devenv shell`, revisa las versiones y vuelve a ejecutar `tinygram-configure` si cambió el SDK. No mezcles herramientas globales con las del entorno.
- **Conflicto de firma o instalación**: detente y comprueba el paquete y la clave usados. No desinstales, no ejecutes `pm clear` y no fuerces una degradación: podrías perder sesión y datos. Conserva la instalación existente hasta decidir una recuperación segura.
- **Cambio que no aparece**: espera a que termine la compilación, confirma la variante y el teléfono, y usa el comando general con `force-stop` y reapertura.

No subas volcados completos de `logcat`, capturas privadas, archivos de sesión o cuerpos de mensajes. Comparte únicamente errores mínimos revisados y saneados, sin credenciales, identificadores privados ni contenido sensible.

## 7. Comprobaciones y cierre

Prueba el entorno y el helper sin usar credenciales reales en los tests:

```bash
devenv test
bash scripts/tests/configure-dev-test.sh
```

La segunda orden ejecuta solo los tests aislados del helper, con un `pass` simulado. `devenv test` comprueba herramientas, SDK/NDK y ese mismo helper, pero no demuestra que la app compile ni que funcione en el teléfono. Según el cambio, añade las pruebas pertinentes:

```bash
./gradlew :core:ui:test
./gradlew :feature:dialog:test
cargo test --manifest-path native/mtproto-rs/Cargo.toml
cargo test --manifest-path native/markup-rs/Cargo.toml
git diff --check
```

Ejecuta solo las pruebas relevantes y registra lo que realmente pasó. Los cambios nativos requieren además su compilación Android y las comprobaciones de contratos Kotlin correspondientes. La verificación con Telegram real y la inspección visual son pasos separados.

Al terminar, puedes detener los daemons de Gradle antes de salir del entorno:

```bash
./gradlew --stop
exit
```

## 8. Resultado de la preparación

Comprobado el 5 de noviembre de 2026, con el teléfono Android 10/API 29, ABI `arm64-v8a`:

- `devenv test`: pasó, con JDK 17 y 25 de Nix, SDK/NDK, los tres destinos Rust, ShellCheck y pruebas aisladas del configurador.
- `./gradlew :app:assembleDebug :core:ui:testDebugUnitTest -PtargetAbi=arm64-v8a`: pasó sin omitir tareas nativas. Los 63 tests de `core/ui` pasaron, sin errores ni tests omitidos. Se verificó además que el daemon usó el JDK 25 de Nix.
- `./gradlew :app:installDebug -PtargetAbi=arm64-v8a`: pasó e instaló en un dispositivo. La ejecución reutilizó 319 de 320 tareas y terminó en 33 segundos.
- APK generado: `app/build/outputs/apk/debug/monogram-arm64-v8a-0.4.3-debug.apk`.
- Watcher: prueba aislada satisfactoria de ejecución inicial, eventos Kotlin/XML y exclusión de archivos generados en `build`. No se probó todavía una edición de interfaz seguida de actualización automática en el teléfono.

Durante la preparación inicial, la orden de apertura se envió, pero su comprobación no terminó. Al cerrar aquella verificación, el 6 de noviembre, ADB ya no detectaba el teléfono. Posteriormente confirmaste que la app abre y que las funciones del login que probaste responden correctamente, sin haber iniciado sesión en Telegram. Esto confirma esas comprobaciones manuales, no una sesión autenticada ni el watcher sobre la app real.

Para continuar, vuelve a conectar y desbloquear el teléfono, entra en `devenv shell`, selecciona `ANDROID_SERIAL` como en el paso 3 y ejecuta:

```bash
timeout 20s adb shell am start -n org.monogram.debug/org.monogram.MainActivity
```

Confirma visualmente la apertura antes de iniciar el watcher. Si la orden expira, detente y diagnostica la conexión y el arranque sin desinstalar ni borrar datos.

## 9. Identidad Tinygram y temas Osaka

La marca visible es **Tinygram** en los diez idiomas existentes. Los identificadores técnicos, nombres de paquetes, recursos, archivos y almacenamiento siguen intactos. Por eso el APK todavía se llama `monogram-arm64-v8a-0.4.3-debug.apk`. Los enlaces y la promoción de patrocinio del proyecto original conservan su identidad y destinatario.

La paleta se inspira en Solarized Osaka y adapta los acentos al contraste de una interfaz móvil:

| Rol | Claro | Oscuro |
| --- | --- | --- |
| Fondo | `#FDF5E2` | `#001419` |
| Panel de chats | `#EDE7D3` | `#002C38` |
| Acento principal | `#1A6265` | `#6ED2C8` |
| Texto principal | `#546A71` | `#ADB7B7` |

El login y el wallpaper predeterminado usan degradados discretos. Las imágenes de fondo elegidas por el usuario no se modifican. Se conservan las formas y animaciones Expressive y los demás presets de acento.

En Ajustes → Apariencia, elige el acento **Tinygram** para esta paleta. El color dinámico sigue disponible en dispositivos compatibles y puede reemplazarla si lo activas. En instalaciones nuevas está desactivado por defecto; se respeta una elección guardada anteriormente. El preset mantiene internamente el identificador `Monogram` para no romper preferencias existentes.

Comprobaciones del 6 de noviembre de 2026 para esta personalización:

- `devenv test`: pasó.
- `./gradlew :core:ui:testDebugUnitTest -PtargetAbi=arm64-v8a`: pasaron 68 pruebas, incluidas las de contraste de roles claros/oscuros y degradados, sin errores ni pruebas omitidas.
- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :core:ui:assembleDebugAndroidTest -PtargetAbi=arm64-v8a`: compiló la app y los APK de pruebas, sin omitir tareas nativas.
- `./gradlew :app:installDebug -PtargetAbi=arm64-v8a`: actualizó la instalación existente por USB, sin desinstalar ni borrar datos.
- Dos pruebas instrumentadas pasaron con `adb shell am instrument -w -e class ...`: `org.monogram.core.ui.AppearanceSettingsTest` verificó las preferencias en almacenamiento aislado y `org.monogram.TinygramAppearanceTest#tinygramBrandResolvesInEverySupportedLocale` verificó la marca en los diez idiomas.
- Se intentaron las dos pruebas de `org.monogram.core.ui.components.MonogramWallpaperTest`, pero no encontraron una jerarquía Compose mientras el teléfono estaba bloqueado y con la pantalla apagada. La prueba `org.monogram.TinygramAppearanceTest#phoneEntryLightHasAccessibleActions` tampoco pudo continuar: Espresso informó que no había una actividad en estado `RESUMED`. Estas tres pruebas no se consideran aprobadas. Queda pendiente reintentarlas con el teléfono desbloqueado y completar el login claro/oscuro, texto ampliado y panel adaptable. No se ha verificado una sesión real de Telegram ni Live Edit.
