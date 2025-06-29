class AlgorithmInfo {
  final String kemAlgorithm;
  final String symmetricAlgorithm;
  final String signatureAlgorithm;

  AlgorithmInfo({
    required this.kemAlgorithm,
    required this.symmetricAlgorithm,
    required this.signatureAlgorithm,
  });

  factory AlgorithmInfo.fromMap(Map<String, dynamic> map) {
    return AlgorithmInfo(
      kemAlgorithm: map['kemAlgorithm'] ?? '',
      symmetricAlgorithm: map['symmetricAlgorithm'] ?? '',
      signatureAlgorithm: map['signatureAlgorithm'] ?? '',
    );
  }
}

class OrganizationInfo {
  final String name;
  final String? id;
  final String? serverVersion;
  final AlgorithmInfo? supportedAlgorithms;

  OrganizationInfo({
    required this.name,
    this.id,
    this.serverVersion,
    this.supportedAlgorithms,
  });

  factory OrganizationInfo.fromMap(Map<String, dynamic> map) {
    return OrganizationInfo(
      name: map['name'] ?? '',
      id: map['id'],
      serverVersion: map['serverVersion'],
      supportedAlgorithms: map['supportedAlgorithms'] != null
          ? AlgorithmInfo.fromMap(
              Map<String, dynamic>.from(map['supportedAlgorithms']))
          : null,
    );
  }
}
