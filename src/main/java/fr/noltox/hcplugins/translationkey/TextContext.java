package fr.noltox.hcplugins.translationkey;

/** Text-only PAPI boundary. Server integrations decide which thread may invoke it. */
@FunctionalInterface
interface TextContext {
    TextContext IDENTITY = value -> value;
    String expand(String value);
}
