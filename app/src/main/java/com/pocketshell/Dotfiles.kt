package com.pocketshell

import java.io.File

/** Default bash startup files, written into the home folder on first launch. */
object Dotfiles {

    /**
     * The terminal capabilities readline needs, as one termcap entry. bash's bundled
     * termcap library uses the TERMCAP variable directly when it holds an entry (not a
     * file path) whose name matches TERM. Without it readline falls back to a "dumb"
     * terminal and line editing redraws badly.
     */
    val XTERM_TERMCAP = listOf(
        "xterm-256color|xterm|Pocket Shell",
        "am", "km", "mi", "ms", "xn", "co#80", "li#24", "Co#256", "it#8",
        "AL=\\E[%dL", "DC=\\E[%dP", "DL=\\E[%dM", "DO=\\E[%dB", "IC=\\E[%d@",
        "LE=\\E[%dD", "RI=\\E[%dC", "UP=\\E[%dA",
        "al=\\E[L", "bl=^G", "cd=\\E[J", "ce=\\E[K", "cl=\\E[H\\E[2J",
        "cm=\\E[%i%d;%dH", "cr=\\r", "cs=\\E[%i%d;%dr", "dc=\\E[P", "dl=\\E[M",
        "do=\\n", "ei=\\E[4l", "ho=\\E[H", "im=\\E[4h", "le=^H", "nd=\\E[C",
        "sr=\\EM", "up=\\E[A",
        "kD=\\E[3~", "kI=\\E[2~", "kN=\\E[6~", "kP=\\E[5~", "kb=\\177",
        "kh=\\EOH", "@7=\\EOF", "ku=\\EOA", "kd=\\EOB", "kr=\\EOC", "kl=\\EOD",
        "ks=\\E[?1h\\E=", "ke=\\E[?1l\\E>",
        "md=\\E[1m", "me=\\E[0m", "mr=\\E[7m", "so=\\E[7m", "se=\\E[27m",
        "us=\\E[4m", "ue=\\E[24m", "vi=\\E[?25l", "ve=\\E[?12l\\E[?25h",
    ).joinToString(":", postfix = ":")

    private const val BASH_PROFILE = """# ~/.bash_profile — read by login shells (every new Pocket Shell session).
[ -f ~/.bashrc ] && . ~/.bashrc
echo "Pocket Shell · bash ${'$'}BASH_VERSION"
"""

    private const val BASHRC = """# ~/.bashrc — Pocket Shell defaults. Edit freely; it is never overwritten.

# Prompt: current folder in cyan, then $ (or # as root).
PS1='\[\e[1;36m\]\w\[\e[0m\] \$ '

HISTSIZE=5000
HISTFILESIZE=10000
HISTCONTROL=ignoredups:erasedups
shopt -s histappend checkwinsize

alias ls='ls --color=auto'
alias ll='ls -la'
alias la='ls -A'
alias grep='grep --color=auto'
"""

    fun installIfMissing(home: File) {
        writeIfMissing(File(home, ".bash_profile"), BASH_PROFILE)
        writeIfMissing(File(home, ".bashrc"), BASHRC)
    }

    private fun writeIfMissing(file: File, content: String) {
        if (!file.exists()) file.writeText(content)
    }
}
