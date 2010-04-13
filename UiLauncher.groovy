class UiLauncher {
    def launch(String accountName, String apiKey) {
        def api = new CampfireApi(accountName, apiKey)
        def sui = new SwingUi(api: api)
        sui.show()
    }
}
