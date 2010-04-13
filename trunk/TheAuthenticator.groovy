import java.net.*

class TheAuthenticator extends Authenticator {
    def user
    def pwd

    PasswordAuthentication getPasswordAuthentication() {
        println 'Handling ' + getRequestingScheme() + ' auth.'
        new PasswordAuthentication(user, pwd.toCharArray())
    }
}
