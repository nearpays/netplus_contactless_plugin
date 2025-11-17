import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:netplus_contactless/netplus_contactless_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelNetplusContactless platform = MethodChannelNetplusContactless();
  const MethodChannel channel = MethodChannel('netplus_contactless');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
          return '42';
        });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });
}
