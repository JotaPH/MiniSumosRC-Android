# MiniSumosRC Android Display

Aplicación Android nativa para mostrar una batalla de MiniSumos RC en pantalla completa, recibir actualizaciones por Bluetooth clásico (SPP) y leer anuncios en español.

## Requisitos

- Android Studio reciente.
- JDK 17 (Android Studio puede usar el JDK incluido).
- Teléfono Android con Bluetooth clásico.
- ESP32 con Bluetooth SPP habilitado. El ESP32-WROOM-32 clásico lo soporta; ESP32-C3/S3 no ofrecen Bluetooth Classic SPP integrado.

## Abrir y ejecutar

1. Descarga este repositorio o usa **Code → Download ZIP**.
2. Descomprime el ZIP.
3. Abre Android Studio y selecciona **Open**, luego elige la carpeta `MiniSumosRC-Android`.
4. Espera a que termine la sincronización de Gradle. Si solicita instalar SDK 35 o JDK 17, acepta.
5. Conecta el teléfono por USB, habilita las opciones de desarrollador y la depuración USB; o usa un emulador (para Bluetooth real, es preferible un teléfono).
6. Pulsa **Run ▶** y selecciona el teléfono.

## Emparejar con la ESP32

1. En el teléfono, abre Ajustes → Bluetooth y empareja la ESP32 si aparece.
2. Abre MiniSumos RC Display, selecciona la ESP32 en la lista y pulsa **Conectar**.
3. La ESP32 debe exponer un servicio Bluetooth Classic SPP y enviar una línea JSON terminada en salto de línea (`\n`).

## Protocolo de mensajes

Enviar UTF-8, un objeto JSON por línea:

```json
{"robot1":"ALPHA","robot2":"GAMMA","timer":"0:00:12","attacks1":2,"attacks2":1,"announcement":"PREPARADOS PARA LA BATALLA"}
```

También se aceptan las claves alternativas `r1`, `r2`, `tiempo`, `ataques1`, `ataques2` y `anuncio`.

## Importante

Esta primera versión es la pantalla Android. **Aún falta integrar el envío de datos desde el firmware ESP32 o desde el programa Python**; por sí sola no recibe datos hasta que el emisor Bluetooth esté programado con el protocolo anterior. La app usa Bluetooth clásico SPP, no BLE.
