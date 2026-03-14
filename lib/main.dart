/// Project AllySong — main entry point.
///
/// Initialises services (sensor fusion, mesh network, AI classifier)
/// and launches the Material app shell.
library;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import 'services/sensor_service.dart';
import 'services/mesh_network_service.dart';
import 'services/ai_classifier_service.dart';
import 'screens/home_screen.dart';
import 'screens/hitl_screen.dart';
import 'screens/chat_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();

  // Lock to portrait — disaster UIs must be instantly readable.
  SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
  ]);

  runApp(const AllySongApp());
}

/// Root widget.  Provides three core services via [MultiProvider] so every
/// screen and widget can access sensor data, mesh status, and AI inference
/// without prop-drilling.
class AllySongApp extends StatelessWidget {
  const AllySongApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        // ── Sensor fusion service (accelerometer → quake detection) ──
        ChangeNotifierProvider(create: (_) => SensorService()),

        // ── Nearby Connections mesh networking ───────────────────────
        ChangeNotifierProvider(create: (_) => MeshNetworkService()),

        // ── TFLite earthquake classifier ─────────────────────────────
        ChangeNotifierProvider(create: (_) => AiClassifierService()),
      ],
      child: MaterialApp(
        title: 'AllySong',
        debugShowCheckedModeBanner: false,
        theme: _buildTheme(),

        // ── Named routes ─────────────────────────────────────────────
        initialRoute: HomeScreen.routeName,
        routes: {
          HomeScreen.routeName: (_) => const HomeScreen(),
          HITLScreen.routeName: (_) => const HITLScreen(),
          ChatScreen.routeName: (_) => const ChatScreen(),
        },
      ),
    );
  }

  /// Dark, high-contrast theme optimised for readability in crisis
  /// scenarios (sunlight, dust, low battery / dimmed screens).
  ThemeData _buildTheme() {
    return ThemeData(
      brightness: Brightness.dark,
      primarySwatch: Colors.red,
      scaffoldBackgroundColor: const Color(0xFF121212),
      appBarTheme: const AppBarTheme(
        backgroundColor: Color(0xFF1E1E1E),
        elevation: 0,
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
          textStyle: const TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.bold,
          ),
        ),
      ),
      textTheme: const TextTheme(
        headlineLarge: TextStyle(
          fontSize: 28,
          fontWeight: FontWeight.bold,
          color: Colors.white,
        ),
        bodyLarge: TextStyle(fontSize: 16, color: Colors.white70),
      ),
    );
  }
}
