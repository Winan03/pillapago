/**
 * Almacén seguro del Access Token en memoria.
 * POR SEGURIDAD FRENTE A XSS: El token vive únicamente en memoria de la aplicación.
 * Nunca se almacena en persistencia del navegador (Web Storage API).
 */
let inMemoryToken: string | null = null;

export const tokenStore = {
  getToken: (): string | null => inMemoryToken,
  setToken: (token: string | null): void => {
    inMemoryToken = token;
  },
  clearToken: (): void => {
    inMemoryToken = null;
  },
};
