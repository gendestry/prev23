

def queens(board, n, nn):
    # print("Start:", n, nn)
    if n == nn:
        i = 0
        while i < nn:
            j = 0
            while j < nn:
                if board[i] == j:
                    print('O', end='')
                else:
                    print('.', end='')
                j += 1

            print()
            i += 1
        
        print()
        solutions = 1
    else:
        solutions = 0
        i = 0
        while i < nn:
            ok = True
            j = 0
            while j < n:
                # print("i:", i, ", j:", j, ", b:", board[j], ", n:", n)
                if board[j] == i:
                    ok = False
                if abs(board[j] - i) == abs(j - n):
                    ok = False
                j += 1
            
            if ok:
                board[n] = i
                solutions = solutions + queens(board, n+1, nn)
            i += 1
        
    return solutions

board = [0] * 64
print(queens(board, 0, 8))