import 'netplus_contactless_platform_interface.dart';

class NetplusContactless {
  static NetplusContactlessPlatform get _platform =>
      NetplusContactlessPlatform.instance;

  Future<bool> hasNFC() {
    return _platform.hasNFC();
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
    return _platform.configureTerminal(
      businessName: businessName,
      partnerName: partnerName,
      partnerId: partnerId,
      terminalId: terminalId,
      terminalSerialNumber: terminalSerialNumber,
      businessAddress: businessAddress,
      customerName: customerName,
      merchantId: merchantId,
      institutionalCode: institutionalCode,
    );
  }

  Future<void> launchContactless({
    required Function(bool status, String data) onLaunchContactless,
    required String pinKey,
    required String keyHolder,
    required double amount,
    String? nfcKitOption,
    double? cashBackAmount,
  }) {
    return _platform.launchContactless(
      onLaunchContactless: onLaunchContactless,
      pinKey: pinKey,
      keyHolder: keyHolder,
      amount: amount,
      cashBackAmount: cashBackAmount,
      nfcKitOption: nfcKitOption,
    );
  }

  /// accountType can be either of savings, credit, current or default.
  /// If it is null, it will be set to default
  Future<dynamic> makePayment({
    required String cardReadData,
    required double amount,
    String? accountType,
    String? remark,
  }) {
    return _platform.makePayment(
      cardReadData: cardReadData,
      amount: amount,
      accountType: accountType,
      remark: remark,
    );
  }

  Future<dynamic> checkBalance({required String cardReadData}) {
    return _platform.checkBalance(cardReadData: cardReadData);
  }
}
