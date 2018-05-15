package x509

// X509Config is the configuration for using X.509 certs to
// authenticate with Spinnaker.
type X509Config struct {
	CertPath string `yaml:"certPath"`
	KeyPath  string `yaml:"keyPath"`
}
