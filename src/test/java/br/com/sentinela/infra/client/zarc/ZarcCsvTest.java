package br.com.sentinela.infra.client.zarc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZarcCsvTest {
    // Cabeçalho e linha copiados do arquivo real da safra 2026/2027.
    private static final String HEADER = "﻿Nome_cultura;SafraIni;SafraFin;Cod_Cultura;Cod_Ciclo;Cod_Solo;geocodigo;UF;municipio;Cod_Clima;"
            + "Nome_Clima;Cod_Outros_Manejos;Nome_Outros_Manejos;Produtividade;Cod_NM;Cod_Munic;Cod_Meso;Cod_Micro;Portaria;"
            + "dec1;dec2;dec3;dec4;dec5;dec6;dec7;dec8;dec9;dec10;dec11;dec12;dec13;dec14;dec15;dec16;dec17;dec18;dec19;dec20;"
            + "dec21;dec22;dec23;dec24;dec25;dec26;dec27;dec28;dec29;dec30;dec31;dec32;dec33;dec34;dec35;dec36";
    private static final String LINE = "Sorgo Granífero 2ª Safra;2026;2027;12016740174021;21;2;3550100;SP;São Manuel;0;Não se aplica;1;Sequeiro;;;"
            + "037255;04;023;Port.290_de_22-07-2026;20;20;20;20;20;20;20;20;20;30;30;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;20";

    @Test
    void readsARealLine() {
        ZarcCsv.Row row = new ZarcCsv(HEADER).parse(LINE);

        assertThat(row.cropName()).isEqualTo("Sorgo Granífero 2ª Safra");
        assertThat(row.cycleCode()).isEqualTo(21);
        assertThat(row.soilCode()).isEqualTo(2);
        assertThat(row.ibgeCode()).isEqualTo(3550100);
        assertThat(row.uf()).isEqualTo("SP");
        assertThat(row.municipalityKey()).isEqualTo("sao manuel");
        assertThat(row.managementName()).isEqualTo("Sequeiro");
        assertThat(row.productivity()).isNull();
        assertThat(row.managementLevel()).isNull();
        assertThat(row.risks()).isEqualTo("222222222330000000000000000000000002");
    }

    @Test
    void brokenLinesAreSkippedInsteadOfStoppingTheImport() {
        ZarcCsv csv = new ZarcCsv(HEADER);
        assertThat(csv.parse("")).isNull();
        assertThat(csv.parse("Soja;2026;2027")).isNull();
        assertThat(csv.parse(LINE.replace(";30;30;", ";35;30;"))).isNull();
    }

    @Test
    void missingColumnIsRejectedUpFront() {
        assertThatThrownBy(() -> new ZarcCsv("Nome_cultura;UF")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void municipalityKeyIgnoresAccentsCaseAndPunctuation() {
        assertThat(ZarcCsv.normalize("São João d'Aliança")).isEqualTo(ZarcCsv.normalize("SAO JOAO D ALIANCA"));
        assertThat(ZarcCsv.normalize("  Embu-Guaçu ")).isEqualTo("embu guacu");
    }
}
