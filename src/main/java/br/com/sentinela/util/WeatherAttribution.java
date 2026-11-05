package br.com.sentinela.util;

/** Crédito e aviso exigidos pelos termos da WeatherAPI para exibir os dados a usuários finais. */
public final class WeatherAttribution {
    public static final String SOURCE = "WeatherAPI.com";
    public static final String DISCLAIMER = "Informações meteorológicas fornecidas pela WeatherAPI.com apenas para fins "
            + "informativos gerais. Previsões e condições são probabilísticas e podem não ser precisas para o seu local ou "
            + "horário. Não use estes dados como única base para decisões que envolvam segurança pessoal, aviação, navegação "
            + "marítima, planejamento de emergências ou outras atividades críticas. Em decisões em que a precisão seja "
            + "essencial, consulte sempre os serviços meteorológicos oficiais e as autoridades competentes.";

    private WeatherAttribution() {
    }
}
