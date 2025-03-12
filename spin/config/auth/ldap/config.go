package ldap

type Config struct {
	Username string `json:"username" yaml:"username"`
	Password string `json:"password" yaml:"password"`
}

func (l *Config) IsValid() bool {
	return l.Username != "" && l.Password != ""
}
