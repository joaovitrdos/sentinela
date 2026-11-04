package br.com.sentinela.util;

import java.lang.StackWalker.Option;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilitário de logging para uso simples em qualquer classe do projeto, sem
 * precisar interagir diretamente com a API de logging (java.util.logging).
 *
 * <p>
 * O {@link Logger} correto é resolvido automaticamente a partir da classe
 * chamadora (via {@link StackWalker}), então basta chamar os métodos estáticos
 * a partir de qualquer classe:
 *
 * <pre>{@code
 * LogUtil.info("Solicitacao {0} processada com sucesso", idSolicitacao);
 * LogUtil.debug("Payload recebido: {0}", payload);
 * LogUtil.warn("Item {0} nao encontrado, ignorando", itemId);
 * LogUtil.error("Falha ao processar requisicao de terceiros", exception);
 * }</pre>
 *
 * <p>
 * Os placeholders seguem o padrao nativo do {@code java.util.logging} (baseado
 * em {@link java.text.MessageFormat}: {@code {0}}, {@code {1}}, ...). A
 * formatacao só é executada se o nível estiver habilitado, então não há custo
 * de performance em montar a mensagem quando o log está desativado.
 *
 * <p>
 * Mapeamento de níveis:
 * <ul>
 * <li>{@link #trace} -&gt; {@link Level#FINEST}</li>
 * <li>{@link #debug} -&gt; {@link Level#FINE}</li>
 * <li>{@link #info} -&gt; {@link Level#INFO}</li>
 * <li>{@link #warn} -&gt; {@link Level#WARNING}</li>
 * <li>{@link #error} -&gt; {@link Level#SEVERE}</li>
 * </ul>
 *
 * <p>
 * Configuração de formato/handlers continua sendo feita da forma padrão do
 * {@code java.util.logging} (arquivo {@code logging.properties}, ou
 * programaticamente) — esta classe não substitui essa configuração, apenas
 * evita que cada classe precise obter e manipular seu próprio {@link Logger}.
 */
public final class LogUtil {

    // Cache dos Logger por nome de classe chamadora, para não refazer o
    // StackWalker em toda chamada de log.
    private static final Map<String, Logger> LOGGER_CACHE = new ConcurrentHashMap<>();

    private static final StackWalker STACK_WALKER
            = StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE);

    private LogUtil() {
        // classe utilitária: não deve ser instanciada
    }

    // ------------------------------------------------------------------
    // TRACE (Level.FINEST)
    // ------------------------------------------------------------------
    public static void trace(String message) {
        log(Level.FINEST, message, null, null);
    }

    public static void trace(String message, Object... args) {
        log(Level.FINEST, message, args, null);
    }

    /**
     * Útil quando montar a mensagem tem custo e você quer evitar isso se o
     * nível estiver desabilitado.
     *
     * @param messageSupplier Lambda que irá gerar a mensagem de erro
     */
    public static void trace(Supplier<String> messageSupplier) {
        logSupplier(Level.FINEST, messageSupplier);
    }

    // ------------------------------------------------------------------
    // DEBUG (Level.FINE)
    // ------------------------------------------------------------------
    public static void debug(String message) {
        log(Level.FINE, message, null, null);
    }

    public static void debug(String message, Object... args) {
        log(Level.FINE, message, args, null);
    }

    public static void debug(Supplier<String> messageSupplier) {
        logSupplier(Level.FINE, messageSupplier);
    }

    // ------------------------------------------------------------------
    // INFO (Level.INFO)
    // ------------------------------------------------------------------
    public static void info(String message) {
        log(Level.INFO, message, null, null);
    }

    public static void info(String message, Object... args) {
        log(Level.INFO, message, args, null);
    }

    // ------------------------------------------------------------------
    // WARN (Level.WARNING)
    // ------------------------------------------------------------------
    public static void warn(String message) {
        log(Level.WARNING, message, null, null);
    }

    public static void warn(String message, Object... args) {
        log(Level.WARNING, message, args, null);
    }

    public static void warn(String message, Throwable throwable) {
        log(Level.WARNING, message, null, throwable);
    }

    // ------------------------------------------------------------------
    // ERROR (Level.SEVERE)
    // ------------------------------------------------------------------
    public static void error(String message) {
        log(Level.SEVERE, message, null, null);
    }

    public static void error(String message, Object... args) {
        log(Level.SEVERE, message, args, null);
    }

    public static void error(String message, Throwable throwable) {
        log(Level.SEVERE, message, null, throwable);
    }

    // ------------------------------------------------------------------
    // Verificação de nível (para trechos condicionais de log)
    // ------------------------------------------------------------------
    public static boolean isTraceEnabled() {
        return callerLogger().isLoggable(Level.FINEST);
    }

    public static boolean isDebugEnabled() {
        return callerLogger().isLoggable(Level.FINE);
    }

    public static boolean isInfoEnabled() {
        return callerLogger().isLoggable(Level.INFO);
    }

    // ------------------------------------------------------------------
    // Implementação interna
    // ------------------------------------------------------------------
    private static void log(Level level, String message, Object[] args, Throwable throwable) {
        Logger logger = callerLogger();
        if (!logger.isLoggable(level)) {
            return; // evita custo de formatação quando o nível está desabilitado
        }
        if (throwable != null) {
            logger.log(level, message, throwable);
        } else if (args != null && args.length > 0) {
            logger.log(level, message, args);
        } else {
            logger.log(level, message);
        }
    }

    private static void logSupplier(Level level, Supplier<String> messageSupplier) {
        Logger logger = callerLogger();
        if (logger.isLoggable(level)) {
            logger.log(level, messageSupplier.get());
        }
    }

    private static Logger callerLogger() {
        String callerClassName = STACK_WALKER.walk(frames -> frames
                        .map(StackWalker.StackFrame::getClassName)
                        .filter(name -> !name.equals(LogUtil.class.getName()))
                        .findFirst())
                .orElse(LogUtil.class.getName());

        return LOGGER_CACHE.computeIfAbsent(callerClassName, Logger::getLogger);
    }
}
