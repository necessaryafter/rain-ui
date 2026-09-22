# AGENTS.md

## Comentários

Só comente o que não dá para entender olhando o código: uma armadilha, uma decisão que parece errada mas não é, uma
ordem que importa, o motivo de um limite, algo que o Minecraft ou uma biblioteca faz por baixo. Se o nome da classe, do
método, do campo ou da constante já diz o que é, não comente. Vale para javadoc também.

- Não precisa: um javadoc em `DataSaveCause.QUIT` dizendo que é quando o jogador sai do servidor.
- Precisa: por que o save da morte roda depois que o inventário cai. Se rodasse antes, um crash logo depois devolveria
ao jogador o que ficou no chão.

