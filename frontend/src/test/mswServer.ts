import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';

export const mockUser = {
  id: '11111111-1111-1111-1111-111111111111',
  email: 'anna@example.com',
  username: 'annakovacs',
  displayName: 'Anna Kovacs',
  totalPoints: 150,
  level: 2,
  nextLevelPoints: 200,
  createdAt: '2026-11-20T10:00:00Z',
};

export const handlers = [
  http.get('/api/v1/system/health', () =>
    HttpResponse.json({
      status: 'UP',
      gateway: 'UP',
      identity: 'UP',
      learning: 'UP',
      social: 'UP',
      ai: 'UP',
    }),
  ),

  http.post('/api/v1/auth/refresh', () =>
    HttpResponse.json({
      accessToken: 'mock-access-token',
      tokenType: 'Bearer',
      expiresInSeconds: 300,
      user: {
        id: mockUser.id,
        email: mockUser.email,
        username: mockUser.username,
        displayName: mockUser.displayName,
      },
    }),
  ),

  http.post('/api/v1/auth/login', () =>
    HttpResponse.json({
      accessToken: 'mock-access-token',
      tokenType: 'Bearer',
      expiresInSeconds: 300,
      user: {
        id: mockUser.id,
        email: mockUser.email,
        username: mockUser.username,
        displayName: mockUser.displayName,
      },
    }),
  ),

  http.post('/api/v1/auth/register', () =>
    HttpResponse.json(mockUser, { status: 201 }),
  ),

  http.post('/api/v1/auth/logout', () =>
    new HttpResponse(null, { status: 204 }),
  ),

  http.get('/api/v1/users/me', () =>
    HttpResponse.json(mockUser),
  ),

  http.patch('/api/v1/users/me', async ({ request }) => {
    const body = (await request.json()) as { displayName?: string };
    return HttpResponse.json({
      ...mockUser,
      displayName: body.displayName || mockUser.displayName,
    });
  }),

  http.get('/api/v1/leaderboard', () =>
    HttpResponse.json({
      entries: [
        {
          rank: 1,
          userId: '22222222-2222-2222-2222-222222222222',
          username: 'bela',
          displayName: 'Bela Kovacs',
          level: 4,
          totalPoints: 500,
        },
        {
          rank: 2,
          userId: mockUser.id,
          username: mockUser.username,
          displayName: mockUser.displayName,
          level: mockUser.level,
          totalPoints: mockUser.totalPoints,
        },
      ],
      myRank: {
        rank: 2,
        userId: mockUser.id,
        username: mockUser.username,
        displayName: mockUser.displayName,
        level: mockUser.level,
        totalPoints: mockUser.totalPoints,
      },
      page: 0,
      size: 10,
      totalElements: 2,
      totalPages: 1,
    }),
  ),
];

export const server = setupServer(...handlers);
