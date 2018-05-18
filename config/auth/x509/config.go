package x509

// X509Config is the configuration for using X.509 certs to
// authenticate with Spinnaker.
type X509Config struct {
	CertPath string `yaml:"certPath"`
	KeyPath  string `yaml:"keyPath"`
	Cert     string `yaml:"cert"` // Cert is base64 encoded PEM block.
	Key      string `yaml:"key"`  // Key is base64 encoded PEM block.
}

func (x *X509Config) IsValid() bool {
	// Only one pair of configs properties should be set.
	pathPropertySet := x.CertPath != "" || x.KeyPath != ""
	pemPropertySet := x.Cert != "" || x.Key != ""
	if pathPropertySet && pemPropertySet {
		return false
	}

	pathPropertyMismatch := (x.CertPath != "" && x.KeyPath == "") || (x.KeyPath != "" && x.CertPath == "")
	pemPropertyMismatch := (x.Cert != "" && x.Key == "") || (x.Key != "" && x.Cert == "")
	if pathPropertyMismatch || pemPropertyMismatch {
		return false
	}

	return true
}
