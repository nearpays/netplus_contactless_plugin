import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'dart:developer';

import 'netplus_contactless_platform_interface.dart';

/// An implementation of [NetplusContactlessPlatform] that uses method channels.
class MethodChannelNetplusContactless extends NetplusContactlessPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('netplus_contactless');

  @override
  Future<bool> hasNFC() async {
    bool res;
    try {
      res = await methodChannel.invokeMethod('hasNFC');
    } catch (e) {
      log('Plugin: hasNFC error', error: e);
      res = false;
      rethrow;
    }
    return res;
  }

  @override
  Future<bool> isGPSEnabled() async {
    bool res;
    try {
      res = await methodChannel.invokeMethod('isGPSEnabled');
    } catch (e) {
      log('Plugin: isGPSEnabled error', error: e);
      res = false;
      rethrow;
    }
    return res;
  }


  @override
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
  }) async {
    dynamic res;
    try {
      res = await methodChannel.invokeMethod('configureTerminal', {
        'businessName': businessName,
        'partnerName': partnerName,
        'partnerId': partnerId,
        'terminalId': terminalId,
        'terminalSerialNumber': terminalSerialNumber,
        'businessAddress': businessAddress,
        'customerName': customerName,
        'mid': merchantId,
        'institutionalCode': institutionalCode,
      });
    } catch (e) {
      log('Plugin: Initialization Error ', error: e);
      res = null;
      rethrow;
    }
    return res;
  }

  Function(bool staus, String data)? _onLaunchContactless;

  @override
  Future<void> launchContactless({
    required Function(bool status, String data) onLaunchContactless,
    required String pinKey,
    required String keyHolder,
    required double amount,
    String? nfcKitOption,
    double? cashBackAmount,
  }) async {
    methodChannel.setMethodCallHandler(_platformCallHandler);
    _onLaunchContactless = onLaunchContactless;
    try {
      await methodChannel.invokeMethod('launchContactless', {
        'pinKey': pinKey,
        'keyHolder': keyHolder,
        'nfcKitOption': nfcKitOption,
        'amount': amount,
        'cashBackAmount': cashBackAmount,
      });
    } catch (e) {
      log('Plugin: LaunchContactless Error', error: e);
      rethrow;
    }
  }

  @override
  Future<void> makePayment({
    required String cardReadData,
    required double amount,
    String? accountType,
    String? remark,
  }) async {
    dynamic res;
    try {
      res = await methodChannel.invokeMethod('makePayment', {
        'remark': remark,
        'amount': amount,
        'accountType': accountType,
        'cardReadData': cardReadData,
      });
    } catch (e) {
      log('Plugin: MakePayment Error ', error: e);
      res = null;
      rethrow;
    }
    return res;
  }

  @override
  Future<void> checkBalance({required String cardReadData}) async {
    dynamic res;
    try {
      res = await methodChannel.invokeMethod('checkBalance', {
        'cardReadData': cardReadData,
      });
    } catch (e) {
      log('Plugin: CheckBalance Error ', error: e);
      res = null;
      rethrow;
    }
    return res;
  }

  Future<void> _platformCallHandler(MethodCall call) async {
    switch (call.method) {
      case 'onLaunchContactlessResult':
        _onLaunchContactless?.call(
          call.arguments['status'] as bool,
          call.arguments['data'] as String,
        );
        break;
      default:
        throw MissingPluginException('Unknown method ${call.method}');
    }
  }
}
