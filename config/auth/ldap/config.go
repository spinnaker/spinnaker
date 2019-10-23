package ldap

type Config struct {
	Username string `yaml:"username"`
	Password string `yaml:"password"`
}

func (l *Config) IsValid() bool {
	return l.Username != "" && l.Password != ""
}
