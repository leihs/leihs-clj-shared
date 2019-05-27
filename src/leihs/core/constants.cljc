(ns leihs.core.constants)

(def USER_SESSION_COOKIE_NAME "leihs-user-session")
(def ANTI_CRSF_TOKEN_COOKIE_NAME "leihs-anti-csrf-token")

(def PASSWORD_AUTHENTICATION_SYSTEM_ID "password")

(def HTTP_UNSAVE_METHODS #{:delete :patch :post :put})
(def HTTP_SAVE_METHODS #{:get :head :options :trace})
