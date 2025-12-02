import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'netplus_contactless_method_channel.dart';

abstract class NetplusContactlessPlatform extends PlatformInterface {
  /// Constructs a NetplusContactlessPlatform.
  NetplusContactlessPlatform() : super(token: _token);

  static final Object _token = Object();

  static NetplusContactlessPlatform _instance =
      MethodChannelNetplusContactless();

  /// The default instance of [NetplusContactlessPlatform] to use.
  ///
  /// Defaults to [MethodChannelNetplusContactless].
  static NetplusContactlessPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [NetplusContactlessPlatform] when
  /// they register themselves.
  static set instance(NetplusContactlessPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<bool> hasNFC() {
    throw UnimplementedError('hasNFC() has not been implemented.');
  }

  Future<bool> isGPSEnabled() {
    throw UnimplementedError('isGPSEnabled() has not been implemented.');
  }
  Future<dynamic> configureTerminal({
    required String businessName,
    required String partnerName,
    required String partnerId,
    required String terminalId,
    String? terminalSerialNumber,
    String? businessAddress,
    String? customerName,
    required String merchantId,
    String? institutionalCode,
  }) {
    throw UnimplementedError('configureTerminal() has not been implemented.');
  }

  Future<void> launchContactless({
    required Function(bool status, String data) onLaunchContactless,
    required String pinKey,
    required String keyHolder,
    required double amount,
    String? nfcKitOption,
    double? cashBackAmount,
  }) {
    throw UnimplementedError('launchContactless() has not been implemented.');
  }

  /// accountType can be either of savings, credit, current or default.
  /// If it is null, it will be set to default
  Future<dynamic> makePayment({
    required String cardReadData,
    required double amount,
    String? accountType,
    String? remark,
  }) {
    throw UnimplementedError('makePayment() has not been implemented.');
  }

  Future<dynamic> checkBalance({required String cardReadData}) {
    throw UnimplementedError('checkBalance() has not been implemented.');
  }
}
