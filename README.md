# 🍓 BerryVision - Documentación del Proyecto

## 1. Propósito de la Aplicación
**BerryVision** es una herramienta de visión artificial diseñada para la industria agrícola, específicamente para el cultivo de fresas. Su objetivo principal es identificar el estado de madurez de las fresas en tiempo real o mediante fotografías, ayudando a los agricultores a optimizar la cosecha.

**Estados detectados:**
*   **Verde:** Fresa en crecimiento, no apta para cosecha.
*   **Madura:** Fresa en su punto óptimo de consumo/venta.
*   **Pasada:** Fresa sobremadurada o en proceso de descomposición.

---

## 2. Arquitectura y Funcionamiento
La aplicación utiliza una **arquitectura híbrida de inferencia**, lo que significa que puede procesar imágenes de dos formas dependiendo de la conexión a internet:

1.  **Modo Local (Edge AI):** Utiliza un modelo **TensorFlow Lite** directamente en el dispositivo. Ideal para zonas de cultivo sin señal de red.
2.  **Modo Cloud (API):** Envía la imagen a un servidor potente vía **Retrofit** para obtener resultados de un modelo más pesado o complejo cuando hay conexión disponible.

### Componentes Clave:
*   **`StrawberryFrameAnalyzer.kt`**: El cerebro de la lógica. Decide si enviar el frame a la nube o al detector local basándose en el estado de `ConnectivityObserver`.
*   **`DetectionViewModel.kt`**: Gestiona el estado de las detecciones y la conectividad para que la UI se actualice reactivamente.
*   **`MainScreen.kt`**: Interfaz de usuario construida en Jetpack Compose que permite alternar entre los modos de uso.

---

## 3. Integración del Modelo Nativo (TFLite)
El procesamiento local se realiza mediante la clase `TFLiteDetector.kt`.

*   **Modelo:** `best_float16.tflite` (ubicado en `assets`).
*   **Arquitectura del Modelo:** Basado en YOLO (posiblemente YOLOv8), con una salida de tensor de `[1, 7, 8400]`.
    *   7 parámetros: `[cx, cy, w, h, score_verde, score_madura, score_pasada]`.
*   **Optimización:** Utiliza `GpuDelegate` para acelerar la inferencia usando el hardware gráfico del dispositivo.
*   **Post-procesamiento:** Implementa **NMS (Non-Maximum Suppression)** para eliminar cuadros delimitadores duplicados y filtrar por umbrales de confianza (0.5).

---

## 4. Integración de la API (Cloud)
Cuando el dispositivo detecta conexión a internet, prioriza el uso de la API REST para ahorrar batería en el dispositivo y obtener mayor precisión.

*   **Tecnología:** Retrofit 2 + OkHttp 3.
*   **Servidor Local:** Actualmente configurado para conectarse a una instancia local de FastAPI a través del emulador de Android (`http://10.0.2.2:8000/`). 
*   **Seguridad:** Se habilitó `android:usesCleartextTraffic="true"` para permitir el tráfico HTTP durante el desarrollo.
*   **Mapeo de Datos:** Se utilizan anotaciones `@SerializedName` de GSON para adaptar de forma transparente los nombres de variables del backend (`"class"`, `"bbox"`) a las propiedades internas de la app (`label`, `box`).
*   **Método:** `POST /detect` enviando la imagen mediante `MultipartBody.Part` bajo la clave `file`.
*   **Fallback:** Si la API falla por timeout o error de servidor, el sistema conmuta automáticamente al modelo local de forma transparente para el usuario.

---

## 5. Modos de Uso y Navegación
La aplicación cuenta con un enrutador de vistas que separa la experiencia de usuario:

### A. Pantalla de Inicio (Home)
Una interfaz de bienvenida construida con Material Design 3 que ofrece el menú principal de acciones:
*   **Analizar Fresa:** Inicia la cámara en modo de escaneo en tiempo real.
*   **Ver Galería:** Abre el selector de imágenes del sistema para un análisis estático.

### B. Tiempo Real (Live Video)
*   Utiliza **CameraX Analysis Case**.
*   Procesa frames de video sincronizadamente usando `runBlocking` para respetar el *backpressure* de la cámara (`STRATEGY_KEEP_ONLY_LATEST`), evitando desbordamientos de memoria.
*   Muestra un `DetectionOverlay` dinámico que dibuja los cuadros sobre la vista de la cámara en vivo.

### C. Resultados de Análisis (Analysis Result Screen)
*   **Captura Directa o Galería:** Al obtener una fotografía, el sistema pausa la cámara y navega a una pantalla dedicada de resultados.
*   **Zoom Interactivo:** Implementa gestos multitáctiles (`detectTransformGestures`) permitiendo hacer zoom libre (pellizcar) y arrastrar la fotografía junto con sus cuadros delimitadores (*bounding boxes*) para una inspección milimétrica.
*   **Panel de Estadísticas (Bottom Sheet):** Muestra el total de fresas detectadas y el promedio de confianza de la inteligencia artificial.

---

## 6. Detalles Técnicos de Implementación e Interfaz
*   **Lenguaje:** Kotlin 2.1.0
*   **UI:** Jetpack Compose con Material Design 3.
*   **Prevención de Errores Comunes:** 
    * El registro de *callbacks* de conectividad se inicializa de forma única (`init`) para evitar fugas de memoria.
    * Funciones de extensión personalizadas (`toOrientedBitmap`) en lugar de sobrescribir métodos estándar de CameraX para prevenir bucles de recursión infinita.
*   **Permisos:** Gestión reactiva de permisos de cámara mediante **Accompanist Permissions**.

---

## 7. Estructura de Archivos Principal
com.example.berryvision/
├── data/
│   └── CloudApiService.kt  # Interfaz Retrofit y Modelos (Detection, AnalysisResponse)
├── ml/
│   └── TFLiteDetector.kt   # Lógica de inferencia local
├── ui/
│   ├── home/
│   │   └── HomeScreen.kt           # Menú principal y bienvenida
│   ├── camera/
│   │   ├── MainScreen.kt           # Enrutador interno de cámara y resultados
│   │   ├── CameraPreview.kt        # Integración CameraX
│   │   ├── DetectionOverlay.kt     # Dibujado de cuadros
│   │   ├── AnalysisResultScreen.kt # Vista de resultados interactiva con Zoom y Estadísticas
│   │   ├── StrawberryFrameAnalyzer # Orquestador de inferencia y Backpressure
│   │   └── DetectionViewModel.kt   # Gestión del estado de la UI (detecciones, imagen, conectividad)
│   └── theme/              # Estilos de la App
└── util/
    ├── ConnectivityObserver.kt # Monitoreo de Red
    └── ImageUtils.kt           # Utilidades de rotación de Bitmap

---

## 8. Configuración del Entorno
Para ejecutar este proyecto localmente, asegúrate de cumplir con los siguientes requisitos:

*   **Android Studio:** Ladybug (2024.2.1) o superior.
*   **JDK:** Java 17.
*   **SDK de Android:** API Level 35 (Android 15) recomendado.
*   **Servidor Backend:** Instancia de FastAPI en ejecución (`uvicorn main:app --host 0.0.0.0 --port 8000`).
*   **Hardware:** Se recomienda un dispositivo físico o emulador con soporte para GLES3 para probar la aceleración por GPU de TFLite.

1. Clona el repositorio.
2. Sincroniza el proyecto con los archivos Gradle.
3. Asegúrate de que el archivo `best_float16.tflite` esté presente en la carpeta `app/src/main/assets`.

*Este documento refleja el estado actual del desarrollo, las optimizaciones de UI y las integraciones de IA del proyecto BerryVision.*
