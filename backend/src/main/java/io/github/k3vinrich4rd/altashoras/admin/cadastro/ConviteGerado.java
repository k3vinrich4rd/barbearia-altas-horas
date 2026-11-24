package io.github.k3vinrich4rd.altashoras.admin.cadastro;

/**
 * A resposta de {@code POST /admin/barbeiros/{id}/convite}: o código aparece uma única vez, para o dono montar o link
 * {@code /convite#<codigo>} e o QR. O {@code toString} não o imprime (R30).
 */
record ConviteGerado(String codigo, String expiraEm) {

    @Override
    public String toString() {
        return "ConviteGerado[código omitido]";
    }
}
