all:
	../gradlew installDist -Prelease.useLastTag=true
	./hal --docs > ../docs/commands.md

complete:
	./hal --print-bash-completion > complete
	@echo 'Run this: source complete && PATH=$$PATH:`pwd`'

clean:
	../gradlew clean -Prelease.useLastTag=true
