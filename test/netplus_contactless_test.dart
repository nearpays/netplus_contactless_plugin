import 'package:flutter_test/flutter_test.dart';
import 'package:netplus_contactless/netplus_contactless_method_channel.dart';
import 'package:netplus_contactless/netplus_contactless_platform_interface.dart';

void main() {
  final NetplusContactlessPlatform initialPlatform =
      NetplusContactlessPlatform.instance;

  test('$MethodChannelNetplusContactless is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelNetplusContactless>());
  });
}
